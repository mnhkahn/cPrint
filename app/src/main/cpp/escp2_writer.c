#include "escp2_writer.h"
#include <stdlib.h>
#include <string.h>
#include <android/log.h>

#define LOG_TAG "Escp2Writer"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

void escp2_buffer_init(escp2_buffer *buf) {
    buf->data = NULL;
    buf->size = 0;
    buf->capacity = 0;
}

void escp2_buffer_append(escp2_buffer *buf, const uint8_t *data, size_t len) {
    if (len == 0) return;
    if (buf->size + len > buf->capacity) {
        size_t new_cap = buf->capacity == 0 ? 4096 : buf->capacity * 2;
        while (new_cap < buf->size + len) new_cap *= 2;
        uint8_t *new_data = (uint8_t *)realloc(buf->data, new_cap);
        if (!new_data) return;
        buf->data = new_data;
        buf->capacity = new_cap;
    }
    memcpy(buf->data + buf->size, data, len);
    buf->size += len;
}

void escp2_buffer_free(escp2_buffer *buf) {
    if (buf->data) {
        free(buf->data);
        buf->data = NULL;
    }
    buf->size = 0;
    buf->capacity = 0;
}

static void append_u8(escp2_buffer *buf, uint8_t val) {
    escp2_buffer_append(buf, &val, 1);
}

static void append_bytes(escp2_buffer *buf, const uint8_t *data, size_t len) {
    escp2_buffer_append(buf, data, len);
}

/**
 * Generate ESC/P2 raster print data for Epson inkjet printers.
 * Converts RGB bitmap to 1bpp black/white and sends via ESC . command.
 *
 * Format per line: ESC . m nL nH [bitmap data...]
 *   m = 0 (black), nL/nH = horizontal dots
 * Bitmap data: MSB-first, 1 = black dot
 */
int escp2_generate_print_data(
    const uint8_t *rgb_data,
    int width,
    int height,
    escp2_buffer *out_buf
) {
    if (!rgb_data || width <= 0 || height <= 0 || !out_buf) {
        LOGE("Invalid arguments");
        return -1;
    }

    LOGD("=== START ESC/P2 generation ===");
    LOGD("Image: %dx%d", width, height);

    // Max width for ESC/P2 to keep data reasonable
    int max_width = 512;
    float scale = 1.0f;
    if (width > max_width) {
        scale = (float)max_width / width;
    }

    int final_width = (int)(width * scale);
    int final_height = (int)(height * scale);

    LOGD("Scale: %f -> %dx%d", scale, final_width, final_height);

    // ESC @ - Initialize printer
    LOGD("Init printer (ESC @)");
    append_u8(out_buf, 0x1B);
    append_u8(out_buf, 0x40);

    // ESC U 1 - Unidirectional printing for better quality
    LOGD("Set unidirectional");
    append_u8(out_buf, 0x1B);
    append_u8(out_buf, 0x55);
    append_u8(out_buf, 0x01);

    // ESC 0 - 1/8 inch line spacing
    LOGD("Set line spacing");
    append_u8(out_buf, 0x1B);
    append_u8(out_buf, 0x30);

    int bytes_per_line = (final_width + 7) / 8;
    LOGD("Bytes per line: %d", bytes_per_line);

    // Allocate scaled RGB buffer
    uint8_t *scaled_rgb = NULL;
    if (scale < 1.0f) {
        scaled_rgb = (uint8_t *)malloc(final_width * final_height * 3);
        if (!scaled_rgb) {
            LOGE("Failed to allocate scaled RGB buffer");
            return -1;
        }
        // Simple nearest-neighbor scaling
        for (int y = 0; y < final_height; y++) {
            int src_y = (int)(y / scale);
            if (src_y >= height) src_y = height - 1;
            for (int x = 0; x < final_width; x++) {
                int src_x = (int)(x / scale);
                if (src_x >= width) src_x = width - 1;
                int src_idx = (src_y * width + src_x) * 3;
                int dst_idx = (y * final_width + x) * 3;
                scaled_rgb[dst_idx] = rgb_data[src_idx];
                scaled_rgb[dst_idx + 1] = rgb_data[src_idx + 1];
                scaled_rgb[dst_idx + 2] = rgb_data[src_idx + 2];
            }
        }
    }

    const uint8_t *src_rgb = (scale < 1.0f) ? scaled_rgb : rgb_data;
    int src_width = (scale < 1.0f) ? final_width : width;

    // Send raster data line by line
    LOGD("Sending %d raster lines...", final_height);
    uint8_t *line_buf = (uint8_t *)malloc(bytes_per_line);
    if (!line_buf) {
        LOGE("Failed to allocate line buffer");
        free(scaled_rgb);
        return -1;
    }

    for (int y = 0; y < final_height; y++) {
        memset(line_buf, 0, bytes_per_line);
        int src_y = (scale < 1.0f) ? y : (int)(y / scale);
        if (src_y >= height) src_y = height - 1;

        for (int x = 0; x < final_width; x++) {
            int src_x = (scale < 1.0f) ? x : (int)(x / scale);
            if (src_x >= width) src_x = width - 1;

            int idx = (src_y * src_width + src_x) * 3;
            int r = src_rgb[idx];
            int g = src_rgb[idx + 1];
            int b = src_rgb[idx + 2];

            // Convert RGB to grayscale
            int gray = (r * 299 + g * 587 + b * 114) / 1000;

            // Threshold: dark pixels become dots
            if (gray < 200) {
                int byte_idx = x / 8;
                int bit_idx = 7 - (x % 8); // MSB-first
                line_buf[byte_idx] |= (1 << bit_idx);
            }
        }

        // ESC . m nL nH [data...]
        append_u8(out_buf, 0x1B);           // ESC
        append_u8(out_buf, 0x2E);           // .
        append_u8(out_buf, 0x00);           // m = 0 (black)
        append_u8(out_buf, final_width & 0xFF);       // nL
        append_u8(out_buf, (final_width >> 8) & 0xFF); // nH
        append_bytes(out_buf, line_buf, bytes_per_line);
    }

    // FF - Form feed (page eject)
    LOGD("Form feed");
    append_u8(out_buf, 0x0C);

    // ESC @ - Final init
    LOGD("Final init");
    append_u8(out_buf, 0x1B);
    append_u8(out_buf, 0x40);

    free(line_buf);
    free(scaled_rgb);

    LOGD("=== END generation, total bytes: %zu ===", out_buf->size);
    return 0;
}

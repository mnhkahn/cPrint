#include "escpr_writer.h"
#include <stdlib.h>
#include <string.h>
#include <android/log.h>

#define LOG_TAG "EscprWriter"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

void escpr_buffer_init(escpr_buffer *buf) {
    buf->data = NULL;
    buf->size = 0;
    buf->capacity = 0;
}

void escpr_buffer_append(escpr_buffer *buf, const uint8_t *data, size_t len) {
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

void escpr_buffer_free(escpr_buffer *buf) {
    if (buf->data) {
        free(buf->data);
        buf->data = NULL;
    }
    buf->size = 0;
    buf->capacity = 0;
}

static void append_u8(escpr_buffer *buf, uint8_t val) {
    escpr_buffer_append(buf, &val, 1);
}

static void append_u16_le(escpr_buffer *buf, uint16_t val) {
    uint8_t b[2] = { val & 0xFF, (val >> 8) & 0xFF };
    escpr_buffer_append(buf, b, 2);
}

static void append_u16_be(escpr_buffer *buf, uint16_t val) {
    uint8_t b[2] = { (val >> 8) & 0xFF, val & 0xFF };
    escpr_buffer_append(buf, b, 2);
}

static void append_u32_le(escpr_buffer *buf, uint32_t val) {
    uint8_t b[4] = { val & 0xFF, (val >> 8) & 0xFF, (val >> 16) & 0xFF, (val >> 24) & 0xFF };
    escpr_buffer_append(buf, b, 4);
}

static void append_u32_be(escpr_buffer *buf, uint32_t val) {
    uint8_t b[4] = { (val >> 24) & 0xFF, (val >> 16) & 0xFF, (val >> 8) & 0xFF, val & 0xFF };
    escpr_buffer_append(buf, b, 4);
}

static void append_bytes(escpr_buffer *buf, const uint8_t *data, size_t len) {
    escpr_buffer_append(buf, data, len);
}

static void append_string(escpr_buffer *buf, const char *str) {
    escpr_buffer_append(buf, (const uint8_t *)str, strlen(str));
}

/* ================================================================
 * ESC/P-R Command Definitions (from epson-escpr-api.c)
 * ================================================================ */

static void cmd_exit_packet_mode(escpr_buffer *buf) {
    LOGD("CMD: ExitPacketMode");
    uint8_t cmd[] = {
        0x00, 0x00, 0x00, 0x1B, 0x01, 0x40, 0x45, 0x4A, 0x4C, 0x20,
        0x31, 0x32, 0x38, 0x34, 0x2E, 0x34, 0x0A, 0x40, 0x45, 0x4A,
        0x4C, 0x20, 0x20, 0x20, 0x20, 0x20, 0x0A
    };
    append_bytes(buf, cmd, sizeof(cmd));
}

static void cmd_init_printer(escpr_buffer *buf) {
    LOGD("CMD: InitPrinter (ESC @)");
    uint8_t cmd[] = { 0x1B, 0x40 };
    append_bytes(buf, cmd, sizeof(cmd));
}

static void cmd_enter_remote(escpr_buffer *buf) {
    LOGD("CMD: EnterRemoteMode");
    uint8_t cmd[] = {
        0x1B, '(', 'R', 0x08, 0x00, 0x00,
        'R', 'E', 'M', 'O', 'T', 'E', '1'
    };
    append_bytes(buf, cmd, sizeof(cmd));
}

static void cmd_remote_ti(escpr_buffer *buf) {
    LOGD("CMD: RemoteTI");
    // TI + 3-byte len + YYYY(BE) MM DD hh mm ss
    // We'll use zeros for time (acceptable for uni-directional)
    uint8_t cmd[] = {
        'T', 'I', 0x08, 0x00, 0x00,
        0x00, 0x00, /* year BE */
        0x00,       /* month */
        0x00,       /* day */
        0x00,       /* hour */
        0x00,       /* minute */
        0x00        /* second */
    };
    append_bytes(buf, cmd, sizeof(cmd));
}

static void cmd_remote_js(escpr_buffer *buf) {
    LOGD("CMD: RemoteJS");
    uint8_t cmd[] = { 'J', 'S', 0x04, 0x00, 0x00, 0x00, 0x00, 0x00 };
    append_bytes(buf, cmd, sizeof(cmd));
}

static void cmd_remote_jh(escpr_buffer *buf) {
    LOGD("CMD: RemoteJH");
    uint8_t cmd[] = {
        'J', 'H', 0x0E, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00, 0x00,
        'E', 'S', 'C', 'P', 'R', 'l', 'i', 'b'
    };
    append_bytes(buf, cmd, sizeof(cmd));
}

static void cmd_remote_hd(escpr_buffer *buf) {
    LOGD("CMD: RemoteHD");
    // HD + 3-byte len + platform + 0xFF
    // platform = 0x03 (Android/Linux)
    uint8_t cmd[] = { 'H', 'D', 0x03, 0x00, 0x00, 0x03, 0xFF };
    append_bytes(buf, cmd, sizeof(cmd));
}

static void cmd_remote_pp(escpr_buffer *buf) {
    LOGD("CMD: RemotePP (paper source auto)");
    // PP + 3-byte len + source bytes
    // Auto select: 0x01 0xFF
    uint8_t cmd[] = { 'P', 'P', 0x03, 0x00, 0x00, 0x01, 0xFF };
    append_bytes(buf, cmd, sizeof(cmd));
}

static void cmd_exit_remote(escpr_buffer *buf) {
    LOGD("CMD: ExitRemoteMode");
    uint8_t cmd[] = { 0x1B, 0x00, 0x00, 0x00 };
    append_bytes(buf, cmd, sizeof(cmd));
}

static void cmd_escpr_mode(escpr_buffer *buf) {
    LOGD("CMD: ESCPRMode");
    uint8_t cmd[] = {
        0x1B, '(', 'R', 0x06, 0x00, 0x00,
        'E', 'S', 'C', 'P', 'R'
    };
    append_bytes(buf, cmd, sizeof(cmd));
}

static void cmd_print_quality(escpr_buffer *buf) {
    LOGD("CMD: PrintQualityCmd");
    // ESC q + paramLen(4LE) + "setq" + 9 bytes
    // mediaType=0(plain), quality=1(normal), colorMode=1(color)
    // brightness=0, contrast=0, saturation=0
    // colorPlane=3(fullcolor), paletteSize=0(BE)
    uint8_t cmd[] = {
        0x1B, 'q', 0x09, 0x00, 0x00, 0x00,
        's', 'e', 't', 'q',
        0x00, // mediaType = plain
        0x01, // quality = normal
        0x01, // colorMode = color
        0x00, // brightness
        0x00, // contrast
        0x00, // saturation
        0x03, // colorPlane = fullcolor
        0x00, 0x00 // paletteSize = 0 (BE)
    };
    append_bytes(buf, cmd, sizeof(cmd));
}

static void cmd_chkcmd(escpr_buffer *buf) {
    LOGD("CMD: Chkcmd");
    // ESC u + paramLen(4LE) + "chku" + 2 bytes
    uint8_t cmd[] = {
        0x1B, 'u', 0x02, 0x00, 0x00, 0x00,
        'c', 'h', 'k', 'u',
        0x01, 0x01
    };
    append_bytes(buf, cmd, sizeof(cmd));
}

static void cmd_job(escpr_buffer *buf, int paper_w, int paper_h,
                    int top_margin, int left_margin,
                    int printable_w, int printable_h,
                    int input_res, int print_dir) {
    LOGD("CMD: JobCmd (paper=%dx%d, printable=%dx%d)", paper_w, paper_h, printable_w, printable_h);
    // ESC j + paramLen(4LE) + "setj" + 22 bytes
    append_u8(buf, 0x1B);
    append_u8(buf, 'j');
    append_u32_le(buf, 0x16);
    append_bytes(buf, (const uint8_t *)"setj", 4);
    append_u32_be(buf, paper_w);
    append_u32_be(buf, paper_h);
    append_u16_be(buf, top_margin);
    append_u16_be(buf, left_margin);
    append_u32_be(buf, printable_w);
    append_u32_be(buf, printable_h);
    append_u8(buf, input_res);
    append_u8(buf, print_dir);
}

static void cmd_start_page(escpr_buffer *buf) {
    LOGD("CMD: StartPage");
    uint8_t cmd[] = {
        0x1B, 'p', 0x00, 0x00, 0x00, 0x00,
        's', 't', 't', 'p'
    };
    append_bytes(buf, cmd, sizeof(cmd));
}

static void cmd_send_line(escpr_buffer *buf, int y, const uint8_t *rgb, int width_pixels) {
    int data_size = width_pixels * 3;
    int param_size = 7 + data_size;

    append_u8(buf, 0x1B);
    append_u8(buf, 'd');
    append_u32_le(buf, param_size);
    append_bytes(buf, (const uint8_t *)"dsnd", 4);
    append_u16_be(buf, 0);
    append_u16_be(buf, y);
    append_u8(buf, 0x00); // no compression
    append_u16_be(buf, data_size);
    append_bytes(buf, rgb, data_size);
}

static void cmd_end_page(escpr_buffer *buf) {
    LOGD("CMD: EndPage");
    uint8_t cmd[] = {
        0x1B, 'p', 0x01, 0x00, 0x00, 0x00,
        'e', 'n', 'd', 'p',
        0x00
    };
    append_bytes(buf, cmd, sizeof(cmd));
}

static void cmd_end_job(escpr_buffer *buf) {
    LOGD("CMD: EndJob");
    uint8_t cmd[] = {
        0x1B, 'j', 0x00, 0x00, 0x00, 0x00,
        'e', 'n', 'd', 'j'
    };
    append_bytes(buf, cmd, sizeof(cmd));
}

static void cmd_remote_je(escpr_buffer *buf) {
    LOGD("CMD: RemoteJE");
    uint8_t cmd[] = { 'J', 'E', 0x01, 0x00, 0x00 };
    append_bytes(buf, cmd, sizeof(cmd));
}

/* ================================================================
 * Main print data generation
 * ================================================================ */

int escpr_generate_print_data(
    const uint8_t *rgb_data,
    int width,
    int height,
    int dpi,
    escpr_buffer *out_buf
) {
    if (!rgb_data || width <= 0 || height <= 0 || !out_buf) {
        LOGE("Invalid arguments");
        return -1;
    }

    LOGD("=== START ESC/P-R generation ===");
    LOGD("Image: %dx%d @ %ddpi", width, height, dpi);

    // EJL header
    LOGD("Step 1: EJL header");
    append_string(out_buf, "@EJL ENTER LANGUAGE=ESC/P-R\r\n");

    // Exit packet mode
    LOGD("Step 2: Exit packet mode");
    cmd_exit_packet_mode(out_buf);

    // Init printer
    LOGD("Step 3: Init printer");
    cmd_init_printer(out_buf);

    // Enter remote mode
    LOGD("Step 4: Enter remote mode");
    cmd_enter_remote(out_buf);

    // Remote TI (time info - helps printer identify job)
    LOGD("Step 5: Remote TI");
    cmd_remote_ti(out_buf);

    LOGD("Step 6: Remote JS");
    cmd_remote_js(out_buf);

    LOGD("Step 7: Remote JH");
    cmd_remote_jh(out_buf);

    LOGD("Step 8: Remote HD");
    cmd_remote_hd(out_buf);

    LOGD("Step 9: Remote PP");
    cmd_remote_pp(out_buf);

    // Exit remote mode
    LOGD("Step 10: Exit remote mode");
    cmd_exit_remote(out_buf);

    // Enter ESC/P-R mode
    LOGD("Step 11: ESCPRMode");
    cmd_escpr_mode(out_buf);

    // Print quality
    LOGD("Step 12: PrintQuality");
    cmd_print_quality(out_buf);

    // Chkcmd (for printer version >= 3, L3118 should be)
    LOGD("Step 13: Chkcmd");
    cmd_chkcmd(out_buf);

    // Job settings - use image dimensions as paper size to ensure match
    LOGD("Step 14: Job settings");
    int paper_w = width;
    int paper_h = height;
    int input_res = 0; // 0 = 360x360
    if (dpi >= 720) input_res = 1;
    else if (dpi >= 600) input_res = 3;

    cmd_job(out_buf, paper_w, paper_h, 0, 0, paper_w, paper_h, input_res, 0);

    // Start page
    LOGD("Step 15: StartPage");
    cmd_start_page(out_buf);

    LOGD("Step 16: Sending %d lines of RGB data", height);
    int line_stride = width * 3;
    for (int y = 0; y < height; y++) {
        const uint8_t *line = rgb_data + y * line_stride;
        cmd_send_line(out_buf, y, line, width);
    }

    // End page
    LOGD("Step 17: EndPage");
    cmd_end_page(out_buf);

    // End job
    LOGD("Step 18: EndJob");
    cmd_end_job(out_buf);

    // Final init
    LOGD("Step 19: Final InitPrinter");
    cmd_init_printer(out_buf);

    // Enter remote for JE
    LOGD("Step 20: EnterRemote for JE");
    cmd_enter_remote(out_buf);

    LOGD("Step 21: RemoteJE");
    cmd_remote_je(out_buf);

    LOGD("Step 22: ExitRemote");
    cmd_exit_remote(out_buf);

    LOGD("=== END generation, total bytes: %zu ===", out_buf->size);
    return 0;
}

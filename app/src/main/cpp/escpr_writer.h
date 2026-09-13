#ifndef ESCPR_WRITER_H
#define ESCPR_WRITER_H

#include <stdint.h>
#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef struct {
    uint8_t *data;
    size_t size;
    size_t capacity;
} escpr_buffer;

void escpr_buffer_init(escpr_buffer *buf);
void escpr_buffer_append(escpr_buffer *buf, const uint8_t *data, size_t len);
void escpr_buffer_free(escpr_buffer *buf);

int escpr_generate_print_data(
    const uint8_t *rgb_data,
    int width,
    int height,
    int dpi,
    escpr_buffer *out_buf
);

#ifdef __cplusplus
}
#endif

#endif

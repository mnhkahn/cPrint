#ifndef ESCP2_WRITER_H
#define ESCP2_WRITER_H

#include <stdint.h>
#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef struct {
    uint8_t *data;
    size_t size;
    size_t capacity;
} escp2_buffer;

void escp2_buffer_init(escp2_buffer *buf);
void escp2_buffer_append(escp2_buffer *buf, const uint8_t *data, size_t len);
void escp2_buffer_free(escp2_buffer *buf);

int escp2_generate_print_data(
    const uint8_t *rgb_data,
    int width,
    int height,
    escp2_buffer *out_buf
);

#ifdef __cplusplus
}
#endif

#endif

package com.etg.media;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

@ExtendWith(MockitoExtension.class)
class S3MediaStoreTest {

  @Mock S3Client s3;

  @Test
  void putObject_returnsS3Uri() {
    when(s3.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
        .thenReturn(PutObjectResponse.builder().build());
    S3MediaStore store = new S3MediaStore(s3, "etg-media");

    String ref = store.store(new byte[]{1, 2}, "image/png", "CLM-1001");

    assertThat(ref).startsWith("s3://etg-media/CLM-1001/").endsWith(".png");
    ArgumentCaptor<PutObjectRequest> req = ArgumentCaptor.forClass(PutObjectRequest.class);
    verify(s3).putObject(req.capture(), any(RequestBody.class));
    assertThat(req.getValue().contentType()).isEqualTo("image/png");
    assertThat(req.getValue().contentLength()).isEqualTo(2L);
  }
}

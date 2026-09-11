package com.etg.media;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class MediaDownloaderTest {

  private MediaDownloader downloader;
  private MockRestServiceServer server;

  private void setUp() {
    RestTemplate http = new RestTemplateBuilder().build();
    server = MockRestServiceServer.bindTo(http).build();
    downloader = new MediaDownloader(http);
  }

  @Test
  void downloadsBytes_prefersResponseContentType() {
    setUp();
    server.expect(requestTo("https://api.twilio.com/m/1")).andExpect(method(HttpMethod.GET))
        .andRespond(withSuccess(new byte[]{1, 2, 3}, MediaType.IMAGE_PNG));
    var dl = downloader.download(new InboundMedia("https://api.twilio.com/m/1", "image/jpeg"));
    assertThat(dl.bytes()).hasSize(3);
    assertThat(dl.contentType()).isEqualTo("image/png");
    server.verify();
  }

  @Test
  void oversized_rejected() {
    setUp();
    byte[] big = new byte[(int) (MediaDownloader.MAX_BYTES + 1)];
    server.expect(requestTo("https://api.twilio.com/m/2")).andExpect(method(HttpMethod.GET))
        .andRespond(withSuccess(big, MediaType.IMAGE_JPEG));
    assertThatThrownBy(() -> downloader.download(new InboundMedia("https://api.twilio.com/m/2", "image/jpeg")))
        .isInstanceOf(MediaDownloader.MediaTooLargeException.class);
    server.verify();
  }
}

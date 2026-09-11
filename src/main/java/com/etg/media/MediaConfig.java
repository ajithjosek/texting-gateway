package com.etg.media;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

@Configuration
public class MediaConfig {

  @Bean
  @ConditionalOnProperty(name = "etg.media.s3-enabled", havingValue = "true")
  MediaStore s3MediaStore(@Value("${etg.media.bucket:etg-media}") String bucket,
                          @Value("${etg.media.region:us-east-1}") String region,
                          @Value("${etg.media.endpoint:}") String endpoint,
                          @Value("${etg.media.access-key:}") String accessKey,
                          @Value("${etg.media.secret-key:}") String secretKey) {
    var builder = S3Client.builder()
        .region(Region.of(region))
        .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build());
    if (!endpoint.isBlank()) {
      builder.endpointOverride(URI.create(endpoint));
    }
    if (!accessKey.isBlank()) {
      builder.credentialsProvider(
          StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)));
    }
    return new S3MediaStore(builder.build(), bucket);
  }

  @Bean
  @ConditionalOnMissingBean(MediaStore.class)
  MediaStore fileMediaStore(@Value("${etg.media.dir:./media}") String dir) {
    return new FileMediaStore(dir);
  }
}

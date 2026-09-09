package com.etg.links;

import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/links")
public class LinkController {
  private final LinkSigner signer;

  public LinkController(LinkSigner signer) { this.signer = signer; }

  @GetMapping("/verify")
  public Map<String, String> verify(@RequestParam("purpose") String purpose,
                                    @RequestParam("token") String token) {
    return Map.of("purpose", purpose, "refId", signer.verify(purpose, token), "valid", "true");
  }
}

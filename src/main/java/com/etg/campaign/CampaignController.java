package com.etg.campaign;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/campaigns")
public class CampaignController {
  private final CampaignService campaigns;
  private final CampaignRepository repo;
  private final CampaignRecipientRepository recipients;

  public CampaignController(CampaignService campaigns, CampaignRepository repo,
                            CampaignRecipientRepository recipients) {
    this.campaigns = campaigns;
    this.repo = repo;
    this.recipients = recipients;
  }

  public record CreateRequest(@NotBlank String name, @NotBlank String templateKey,
                              String locale, Map<String, Object> vars,
                              List<String> phones, String createdBy) {}
  public record ReviewRequest(String approver) {}

  @PostMapping
  public Campaign create(@Valid @RequestBody CreateRequest r) {
    return campaigns.create(r.name(), r.templateKey(), r.locale(), r.vars(), r.phones(), r.createdBy());
  }

  @GetMapping
  public List<Campaign> list() {
    return repo.findAllByOrderByCreatedAtDesc();
  }

  @GetMapping("/{id}")
  public Campaign get(@PathVariable Long id) {
    return repo.findById(id)
        .orElseThrow(() -> new CampaignService.NotFoundException("CAMPAIGN_NOT_FOUND: " + id));
  }

  @GetMapping("/{id}/recipients")
  public List<CampaignRecipient> recipients(@PathVariable Long id) {
    get(id);
    return recipients.findByCampaignIdOrderByIdAsc(id);
  }

  @PostMapping("/{id}/submit")
  public Campaign submit(@PathVariable Long id) {
    return campaigns.submit(id);
  }

  @PostMapping("/{id}/approve")
  public Campaign approve(@PathVariable Long id, @RequestBody(required = false) ReviewRequest r) {
    return campaigns.approve(id, r == null ? null : r.approver());
  }

  @PostMapping("/{id}/reject")
  public Campaign reject(@PathVariable Long id, @RequestBody(required = false) ReviewRequest r) {
    return campaigns.reject(id, r == null ? null : r.approver());
  }

  @PostMapping("/{id}/launch")
  public Campaign launch(@PathVariable Long id) {
    return campaigns.launch(id);
  }
}

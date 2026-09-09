package com.etg.fnol;

import jakarta.persistence.*;
import java.time.Instant;

/** SMS-conlected FNOL progress. Stateless SMS needs server-side session memory. */
@Entity
@Table(name = "fnol_sessions")
public class FnolSession {
  public static final String VERIFY_POLICY = "VERIFY_POLICY";
  public static final String LOSS_DATE = "LOSS_DATE";
  public static final String LOSS_TYPE = "LOSS_TYPE";

  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  @Column(nullable = false, unique = true) private String phoneE164;
  @Column(nullable = false) private String step = VERIFY_POLICY;
  private String policyNumber;
  private String lossDate;
  @Column(nullable = false) private Instant createdAt = Instant.now();
  @Column(nullable = false) private Instant updatedAt = Instant.now();

  protected FnolSession() {}

  public FnolSession(String phoneE164) {
    this.phoneE164 = phoneE164;
  }

  public Long getId() { return id; }
  public String getPhoneE164() { return phoneE164; }
  public String getStep() { return step; }
  public void setStep(String step) { this.step = step; }
  public String getPolicyNumber() { return policyNumber; }
  public void setPolicyNumber(String policyNumber) { this.policyNumber = policyNumber; }
  public String getLossDate() { return lossDate; }
  public void setLossDate(String lossDate) { this.lossDate = lossDate; }
  public Instant getUpdatedAt() { return updatedAt; }
  public void touch(Instant now) { this.updatedAt = now; }
}

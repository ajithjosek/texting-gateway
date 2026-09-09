package com.etg.policycenter;

import java.time.LocalDate;

public record RenewalCandidate(String policyNumber, String phoneE164, String firstName,
                               LocalDate dueDate, String lob) {}

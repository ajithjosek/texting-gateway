package com.etg.messaging;

import com.google.i18n.phonenumbers.PhoneNumberUtil;
import org.springframework.stereotype.Service;

/** E.164 validation on every ingress edge (send API + consent capture). */
@Service
public class PhoneValidator {
  private final TwilioLookupClient lookup;

  public PhoneValidator(TwilioLookupClient lookup) { this.lookup = lookup; }

  public void validate(String raw) {
    if (raw == null || !raw.startsWith("+")) {
      throw new InvalidPhoneException("PHONE_INVALID_not_e164: " + raw);
    }
    try {
      // Possible-number check (length/plausibility incl. fictional 555 ranges used in tests).
      // Strict carrier validation happens via Lookup line intelligence when credentials exist.
      var proto = PhoneNumberUtil.getInstance().parse(raw, "ZZ");
      if (!PhoneNumberUtil.getInstance().isPossibleNumber(proto)) {
        throw new InvalidPhoneException("PHONE_INVALID_not_dialable: " + raw);
      }
    } catch (InvalidPhoneException e) {
      throw e;
    } catch (Exception e) {
      throw new InvalidPhoneException("PHONE_INVALID_unparseable: " + raw);
    }
    if (lookup.lineType(raw).map("landline"::equals).orElse(false)) {
      throw new InvalidPhoneException("PHONE_NOT_SMS_CAPABLE_landline: " + raw);
    }
  }
}

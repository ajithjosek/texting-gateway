package com.etg.messaging;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PhoneValidatorTest {

  @Mock TwilioLookupClient lookup;

  private PhoneValidator validator() {
    return new PhoneValidator(lookup);
  }

  @Test
  void acceptsValidE164_mobileLine() {
    when(lookup.lineType("+14155552671")).thenReturn(Optional.of("mobile"));
    assertThatCode(() -> validator().validate("+14155552671")).doesNotThrowAnyException();
  }

  @Test
  void acceptsTestRange_whenLookupUnconfigured() {
    when(lookup.lineType("+15550009000")).thenReturn(Optional.empty());
    assertThatCode(() -> validator().validate("+15550009000")).doesNotThrowAnyException();
  }

  @Test
  void rejectsMissingPlus() {
    assertThatThrownBy(() -> validator().validate("4155552671"))
        .isInstanceOf(InvalidPhoneException.class).hasMessageContaining("not_e164");
    verifyNoInteractions(lookup);
  }

  @Test
  void rejectsGarbageAndTooShort() {
    assertThatThrownBy(() -> validator().validate("+abc")).isInstanceOf(InvalidPhoneException.class);
    assertThatThrownBy(() -> validator().validate("+1555")).isInstanceOf(InvalidPhoneException.class);
    verifyNoInteractions(lookup);
  }

  @Test
  void rejectsLandline_fromLookup() {
    when(lookup.lineType("+14155552671")).thenReturn(Optional.of("landline"));
    assertThatThrownBy(() -> validator().validate("+14155552671"))
        .isInstanceOf(InvalidPhoneException.class).hasMessageContaining("landline");
  }
}

package com.haggisandchips.fantasyfootball.squad;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.haggisandchips.fantasyfootball.auth.FplAuthClient;
import com.haggisandchips.fantasyfootball.domain.Player;
import com.haggisandchips.fantasyfootball.domain.TransferContext;
import com.haggisandchips.fantasyfootball.domain.TransferSuggestion;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// Submits a suggested transfer to FPL's own (undocumented) transfers endpoint - the same request
// fantasy.premierleague.com/transfers makes when you click "Confirm Transfers" there. Reverse
// engineered, not a public API, so FPL could change its shape without notice; the caller sees the
// raw response body on failure (see FplSessionAuthClient) to help diagnose that if it happens.
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "fpl.auth", name = "enabled", havingValue = "true")
public class AuthenticatedTransferExecutor implements TransferExecutor {

  private static final URI TRANSFERS_URI = URI.create("https://fantasy.premierleague.com/api/transfers/");

  private final FplAuthClient fplAuthClient;

  private final ObjectMapper objectMapper;

  @Override
  public void execute(final TransferContext transferContext, final TransferSuggestion suggestion)
      throws IOException, InterruptedException {

    final String requestBody = objectMapper.writeValueAsString(toRequest(transferContext, suggestion));

    log.info("Submitting transfer request: {}", requestBody);
    final String responseBody = fplAuthClient.authenticatedPost(TRANSFERS_URI, requestBody);
    log.info("Transfer request succeeded: {}", responseBody);
  }

  private static TransferRequest toRequest(final TransferContext transferContext, final TransferSuggestion suggestion) {

    final List<TransferPick> picks = new ArrayList<>();
    for (final Map.Entry<Player, Player> transfer : suggestion.getTransfers().entrySet()) {
      final Player playerOut = transfer.getKey();
      final Player playerIn = transfer.getValue();

      picks.add(new TransferPick(
          playerIn.getFantasyId(), playerOut.getFantasyId(),
          toTenths(playerIn.getCostNow()), toTenths(playerOut.getSellingPrice())));
    }

    return new TransferRequest(transferContext.entryId(), transferContext.currentEvent(), picks);
  }

  private static int toTenths(final BigDecimal pounds) {

    return pounds.multiply(BigDecimal.TEN).intValueExact();
  }

  @Data
  private static class TransferRequest {

    private final int entry;

    private final int event;

    private final List<TransferPick> transfers;

    private final Object chip = null;
  }

  @Data
  private static class TransferPick {

    @JsonProperty("element_in")
    private final int elementIn;

    @JsonProperty("element_out")
    private final int elementOut;

    @JsonProperty("purchase_price")
    private final int purchasePrice;

    @JsonProperty("selling_price")
    private final int sellingPrice;
  }
}

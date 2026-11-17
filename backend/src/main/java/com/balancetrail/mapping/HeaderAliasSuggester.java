package com.balancetrail.mapping;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Maps columns by their header names, without AI. Handles the common names ("Txn Ref",
 * "Amount (INR)", "Settlement Dt"), works offline, costs nothing, and is the fallback whenever
 * the AI is off, down, or returns something invalid.
 *
 * <p>Only exact matches of a known name (after lower-casing and dropping spaces and symbols)
 * count. "Gross Amount" vs "Net Amount" is not guessed: that field is left empty for the AI or
 * the analyst.
 */
public class HeaderAliasSuggester {
  private static final List<String> TRANSACTION_ID =
      List.of(
          "transactionid", "txnid", "txnref", "transactionref", "transactionreference", "txnreference",
          "referenceid", "refid", "refno", "referenceno", "referencenumber", "paymentid", "paymentref",
          "txnno", "transactionno", "transactionnumber", "tranid", "merchanttxnid", "merchanttransactionid",
          "utr", "utrno", "utrnumber", "rrn");
  private static final List<String> ACCOUNT_NUMBER =
      List.of(
          "accountnumber", "accountno", "accno", "acctno", "acno", "acnumber", "account", "accountid",
          "customeraccount", "customeraccountnumber", "customeraccountno", "beneficiaryaccount",
          "beneficiaryaccountnumber", "merchantaccountnumber");
  private static final List<String> AMOUNT =
      List.of(
          "amount", "amt", "txnamount", "txnamt", "transactionamount", "amountinr", "amountrs",
          "settlementamount", "settledamount", "paidamount", "paymentamount", "grossamount");
  private static final List<String> TRANSACTION_DATE =
      List.of(
          "transactiondate", "txndate", "txndt", "date", "settlementdate", "settlementdt", "settleddate",
          "valuedate", "paymentdate", "transactiontime", "txntime", "txndatetime", "createdon", "createdat");

  public ColumnMapping suggest(List<String> headers) {
    Set<String> used = new HashSet<>();
    String transactionId = find(headers, TRANSACTION_ID, used);
    String accountNumber = find(headers, ACCOUNT_NUMBER, used);
    String amount = find(headers, AMOUNT, used);
    String transactionDate = find(headers, TRANSACTION_DATE, used);
    return new ColumnMapping(transactionId, accountNumber, amount, transactionDate);
  }

  /** The header matching the earliest alias in the list, or null if none matches. */
  private String find(List<String> headers, List<String> aliases, Set<String> used) {
    for (String alias : aliases) {
      List<String> matches = new ArrayList<>();
      for (String header : headers) {
        if (!used.contains(header) && normalize(header).equals(alias)) {
          matches.add(header);
        }
      }
      if (matches.size() == 1) {
        used.add(matches.get(0));
        return matches.get(0);
      }
    }
    return null;
  }

  static String normalize(String header) {
    return header.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
  }
}

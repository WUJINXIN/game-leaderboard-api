
package com.example.billing;

import java.math.BigDecimal;

public class BillingCalculator {

	public BigDecimal calculate(BigDecimal monthlyFee, int includedUsage, int actualUsage, BigDecimal extraUsagePrice) {

		// Validate required monetary values.
		if (monthlyFee == null || extraUsagePrice == null) {
			throw new IllegalArgumentException("Monetary values must not be null");
		}

		// Reject negative values.
		if (monthlyFee.signum() < 0 || extraUsagePrice.signum() < 0 || includedUsage < 0 || actualUsage < 0) {

			throw new IllegalArgumentException("Billing values must not be negative");
		}

		// Calculate the number of requests beyond the plan limit.
		long extraUsage = Math.max(0L, (long) actualUsage - includedUsage);

		// Calculate the additional charge.
		BigDecimal additionalCharge = extraUsagePrice.multiply(BigDecimal.valueOf(extraUsage));

		// Return the total amount before tax.
		return monthlyFee.add(additionalCharge);
	}
}

package com.example.billing;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class BillingCalculatorTest {

	private BillingCalculator calculator;

	@BeforeEach
	void setUp() {
		calculator = new BillingCalculator();
	}

	@ParameterizedTest
	@CsvSource({ "50, 980", "100, 980", "150, 1080", "200, 1180" })
	void shouldCalculateMonthlyBill(int actualUsage, String expectedAmount) {

		BigDecimal result = calculator.calculate(new BigDecimal("980"), 100, actualUsage, new BigDecimal("2"));

		assertEquals(0, result.compareTo(new BigDecimal(expectedAmount)));
	}

	@Test
	void shouldRejectNegativeUsage() {
		assertThrows(IllegalArgumentException.class,
				() -> calculator.calculate(new BigDecimal("980"), 100, -1, new BigDecimal("2")));
	}

	@Test
	void shouldRejectNegativePrice() {
		assertThrows(IllegalArgumentException.class,
				() -> calculator.calculate(new BigDecimal("-980"), 100, 150, new BigDecimal("2")));
	}

	@Test
	void shouldRejectNullFee() {
		assertThrows(IllegalArgumentException.class, () -> calculator.calculate(null, 100, 150, new BigDecimal("2")));
	}
}

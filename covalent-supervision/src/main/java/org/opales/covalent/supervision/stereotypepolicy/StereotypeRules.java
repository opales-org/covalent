/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.stereotypepolicy;

import java.util.ArrayList;
import java.util.List;

/**
 * The rules of the stereotype table, as plain functions: which rule a name matches, whether a
 * new rule is acceptable, and how the rules a user added or removed are kept from one run to the
 * next.
 */
public final class StereotypeRules {

	/**
	 * The longest stereotype, in characters: what the component card has room for, even next to
	 * its widest status.
	 */
	public static final int MAX_LABEL_LENGTH = 12;

	/** Why a new rule is not acceptable. */
	public enum Problem {
		/** No name ending given. */
		SUFFIX_EMPTY,
		/** The name ending holds something a type name cannot. */
		SUFFIX_INVALID,
		/** No stereotype given. */
		LABEL_EMPTY,
		/** The stereotype is longer than {@link StereotypeRules#MAX_LABEL_LENGTH}. */
		LABEL_TOO_LONG,
		/** Another rule already has this name ending. */
		SUFFIX_EXISTS
	}

	private static final char SEPARATOR = '=';

	private StereotypeRules() {
	}

	/**
	 * The stereotype of a component: the one of the rule whose name ending is the longest among
	 * those its simple name ends with.
	 *
	 * @param rules      the stereotype table
	 * @param simpleName a component's simple name
	 * @return the stereotype, {@code null} when no rule matches
	 */
	public static String match(final List<StereotypeRule> rules, final String simpleName) {
		StereotypeRule best = null;
		for (StereotypeRule rule : rules) {
			if (simpleName.endsWith(rule.suffix()) && (best == null || rule.suffix().length() > best.suffix().length())) {
				best = rule;
			}
		}
		return best == null ? null : best.label();
	}

	/**
	 * Every rule a name matches, the longest name ending first - the first one is the winner.
	 *
	 * @param rules      the stereotype table
	 * @param simpleName a component's simple name
	 * @return the matching rules, none when no rule matches
	 */
	public static List<StereotypeRule> matching(final List<StereotypeRule> rules, final String simpleName) {
		List<StereotypeRule> matching = new ArrayList<>();
		for (StereotypeRule rule : rules) {
			if (simpleName.endsWith(rule.suffix())) {
				matching.add(rule);
			}
		}
		matching.sort((first, second) -> second.suffix().length() - first.suffix().length());
		return matching;
	}

	/**
	 * Whether a rule can be added to the table.
	 *
	 * @param rules  the stereotype table as it is
	 * @param suffix the new rule's name ending, already trimmed
	 * @param label  the new rule's stereotype, already trimmed
	 * @return what is wrong with it, {@code null} when it can be added
	 */
	public static Problem check(final List<StereotypeRule> rules, final String suffix, final String label) {
		if (suffix.isEmpty()) {
			return Problem.SUFFIX_EMPTY;
		}
		if (!suffix.chars().allMatch(Character::isJavaIdentifierPart)) {
			return Problem.SUFFIX_INVALID;
		}
		if (label.isEmpty()) {
			return Problem.LABEL_EMPTY;
		}
		if (label.length() > MAX_LABEL_LENGTH) {
			return Problem.LABEL_TOO_LONG;
		}
		for (StereotypeRule rule : rules) {
			if (rule.suffix().equals(suffix)) {
				return Problem.SUFFIX_EXISTS;
			}
		}
		return null;
	}

	/**
	 * Returns the rules the user added, in the form kept between two runs.
	 *
	 * @param rules the stereotype table
	 * @return the rules the user added, as kept between two runs: one {@code ending=stereotype}
	 *         text each
	 */
	public static List<String> encode(final List<StereotypeRule> rules) {
		List<String> stored = new ArrayList<>();
		for (StereotypeRule rule : rules) {
			if (!rule.builtIn()) {
				stored.add(rule.suffix() + SEPARATOR + rule.label());
			}
		}
		return stored;
	}

	/**
	 * Returns the name endings of the built-in rules the user removed, in the form kept between
	 * two runs.
	 *
	 * @param rules the stereotype table
	 * @return the name ending of every built-in rule missing from the table
	 */
	public static List<String> removedBuiltIns(final List<StereotypeRule> rules) {
		List<String> removed = new ArrayList<>();
		for (StereotypeRule builtIn : BuiltInStereotypes.rules()) {
			if (!rules.contains(builtIn)) {
				removed.add(builtIn.suffix());
			}
		}
		return removed;
	}

	/**
	 * Rebuilds the table from the rules kept between two runs: the built-in rules, then every
	 * kept rule that is still acceptable - a damaged or duplicate one is dropped.
	 *
	 * @param stored what {@link #encode} produced
	 * @return the stereotype table
	 */
	public static List<StereotypeRule> restore(final List<String> stored) {
		return restore(stored, List.of());
	}

	/**
	 * Rebuilds the table from what was kept between two runs: the built-in rules but the removed
	 * ones, then every kept rule that is still acceptable - a damaged or duplicate one is dropped.
	 *
	 * @param stored          what {@link #encode} produced
	 * @param removedBuiltIns what {@link #removedBuiltIns} produced
	 * @return the stereotype table
	 */
	public static List<StereotypeRule> restore(final List<String> stored, final List<String> removedBuiltIns) {
		List<StereotypeRule> rules = new ArrayList<>();
		for (StereotypeRule builtIn : BuiltInStereotypes.rules()) {
			if (!removedBuiltIns.contains(builtIn.suffix())) {
				rules.add(builtIn);
			}
		}
		for (String text : stored) {
			int separator = text.indexOf(SEPARATOR);
			if (separator < 0) {
				continue;
			}
			String suffix = text.substring(0, separator).trim();
			String label = text.substring(separator + 1).trim();
			if (check(rules, suffix, label) == null) {
				rules.add(new StereotypeRule(suffix, label, false));
			}
		}
		return List.copyOf(rules);
	}
}

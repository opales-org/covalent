/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.stereotypepolicy;

import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

/** The stereotype table's rules: which stereotype a name gets, which rule is acceptable, what is kept. */
public class StereotypeRulesTests {

	private static final List<StereotypeRule> BUILT_IN = BuiltInStereotypes.rules();

	private static List<StereotypeRule> withUserRules(final StereotypeRule... added) {
		List<StereotypeRule> rules = new ArrayList<>(BUILT_IN);
		rules.addAll(List.of(added));
		return rules;
	}

	@Test
	public void theBuiltInRulesAreTheMvicRolesAndTheUsualManagersAndStores() {
		List<String> suffixes = BUILT_IN.stream().map(StereotypeRule::suffix).toList();

		Assert.assertEquals(List.of("Controller", "Interaction", "Manager", "Model", "Store", "View"), suffixes);
		Assert.assertTrue(BUILT_IN.stream().allMatch(StereotypeRule::builtIn));
		Assert.assertTrue(BUILT_IN.stream().allMatch(rule -> rule.suffix().equals(rule.label())));
	}

	@Test
	public void aNameTakesTheStereotypeOfItsEnding() {
		Assert.assertEquals("Model", StereotypeRules.match(BUILT_IN, "WatchlistModel"));
		Assert.assertEquals("Store", StereotypeRules.match(BUILT_IN, "OrderStore"));
		Assert.assertEquals("a name that is only the ending matches too", "View", StereotypeRules.match(BUILT_IN, "View"));
	}

	@Test
	public void aNameWithNoMatchingEndingGetsNone() {
		Assert.assertNull(StereotypeRules.match(BUILT_IN, "EcosystemDemo"));
		Assert.assertNull("only the end of the name counts", StereotypeRules.match(BUILT_IN, "ModelBuilder"));
		Assert.assertNull("an anonymous class has no name", StereotypeRules.match(BUILT_IN, ""));
		Assert.assertNull("the match is case-sensitive", StereotypeRules.match(BUILT_IN, "Watchlistmodel"));
	}

	@Test
	public void theLongestMatchingEndingWins() {
		List<StereotypeRule> rules = withUserRules(new StereotypeRule("InteractionController", "Inter. Ctrl", false));

		Assert.assertEquals("Inter. Ctrl", StereotypeRules.match(rules, "WatchlistInteractionController"));
		Assert.assertEquals("without that rule the shorter ending still applies", "Controller",
				StereotypeRules.match(BUILT_IN, "WatchlistInteractionController"));
	}

	@Test
	public void theMatchingEndingsAreListedLongestFirst() {
		List<StereotypeRule> rules = withUserRules(new StereotypeRule("InteractionController", "Inter. Ctrl", false));

		List<String> suffixes = StereotypeRules.matching(rules, "WatchlistInteractionController").stream()
				.map(StereotypeRule::suffix).toList();

		Assert.assertEquals(List.of("InteractionController", "Controller"), suffixes);
		Assert.assertTrue(StereotypeRules.matching(rules, "EcosystemDemo").isEmpty());
	}

	@Test
	public void aNewRuleIsAcceptedWhenItIsCompleteAndNew() {
		Assert.assertNull(StereotypeRules.check(BUILT_IN, "Repository", "Repository"));
		Assert.assertNull("a stereotype of exactly the maximum length", StereotypeRules.check(BUILT_IN, "Repository",
				"x".repeat(StereotypeRules.MAX_LABEL_LENGTH)));
	}

	@Test
	public void aNewRuleIsRefusedWithTheReasonWhy() {
		Assert.assertEquals(StereotypeRules.Problem.SUFFIX_EMPTY, StereotypeRules.check(BUILT_IN, "", "Repo"));
		Assert.assertEquals(StereotypeRules.Problem.SUFFIX_INVALID, StereotypeRules.check(BUILT_IN, "Repo sitory", "Repo"));
		Assert.assertEquals(StereotypeRules.Problem.SUFFIX_INVALID, StereotypeRules.check(BUILT_IN, "Repo-sitory", "Repo"));
		Assert.assertEquals(StereotypeRules.Problem.LABEL_EMPTY, StereotypeRules.check(BUILT_IN, "Repository", ""));
		Assert.assertEquals(StereotypeRules.Problem.LABEL_TOO_LONG, StereotypeRules.check(BUILT_IN, "Repository",
				"x".repeat(StereotypeRules.MAX_LABEL_LENGTH + 1)));
		Assert.assertEquals("a built-in ending cannot be given another stereotype", StereotypeRules.Problem.SUFFIX_EXISTS,
				StereotypeRules.check(BUILT_IN, "Model", "Modèle"));
		Assert.assertEquals(StereotypeRules.Problem.SUFFIX_EXISTS, StereotypeRules.check(
				withUserRules(new StereotypeRule("Repository", "Repo", false)), "Repository", "Other"));
	}

	@Test
	public void onlyTheRulesTheUserAddedAreKept() {
		List<StereotypeRule> rules = withUserRules(new StereotypeRule("Repository", "Repo", false),
				new StereotypeRule("Gateway", "Gate=way", false));

		List<String> stored = StereotypeRules.encode(rules);

		Assert.assertEquals(List.of("Repository=Repo", "Gateway=Gate=way"), stored);
		Assert.assertEquals("what is kept comes back as it was", rules, StereotypeRules.restore(stored));
	}

	@Test
	public void aDamagedOrDuplicateKeptRuleIsDropped() {
		List<StereotypeRule> restored = StereotypeRules.restore(List.of("no separator", "=NoEnding", "Empty=", "Model=Overridden",
				"Too long=Whatever", "Fine=Fine", "Fine=Again", "Long=" + "x".repeat(StereotypeRules.MAX_LABEL_LENGTH + 1)));

		Assert.assertEquals(withUserRules(new StereotypeRule("Fine", "Fine", false)), restored);
	}

	@Test
	public void aRemovedBuiltInRuleIsKeptRemoved() {
		List<StereotypeRule> rules = new ArrayList<>(BUILT_IN);
		rules.removeIf(rule -> rule.suffix().equals("Model"));
		rules.add(new StereotypeRule("Model", "Data", false));

		List<String> removed = StereotypeRules.removedBuiltIns(rules);

		Assert.assertEquals(List.of("Model"), removed);
		Assert.assertEquals(rules, StereotypeRules.restore(StereotypeRules.encode(rules), removed));
	}

	@Test
	public void withNothingKeptTheTableIsTheBuiltInRules() {
		Assert.assertEquals(BUILT_IN, StereotypeRules.restore(List.of()));
	}
}

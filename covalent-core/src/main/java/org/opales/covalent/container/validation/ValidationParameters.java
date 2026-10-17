/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.validation;

import org.opales.covalent.container.CovalentContainerParameters.VerificationLevel;
import org.opales.covalent.container.component.annotations.Parameters;

/**
 * Configures structural and dynamic validation behavior.
 */
@Parameters
public interface ValidationParameters {

	/**
	 * Sets the verification level for structural rules.
	 *
	 * @param structuralRulesChecking structural verification level
	 */
	void setStructuralRulesVerificationLevel(VerificationLevel structuralRulesChecking);

	/**
	 * Returns the verification level for structural rules.
	 *
	 * @return structural verification level
	 */
	VerificationLevel getStructuralRulesVerificationLevel();

	/**
	 * Sets the verification level for dynamic rules.
	 *
	 * @param dynamicRulesChecking dynamic verification level
	 */
	void setDynamicRulesVerificationLevel(final VerificationLevel dynamicRulesChecking);

	/**
	 * Returns the verification level for dynamic rules.
	 *
	 * @return dynamic verification level
	 */
	VerificationLevel getDynamicRulesVerificationLevel();

	/**
	 * Enables or disables produced events field naming convention checks.
	 *
	 * @param checkProduceEventsFieldNamingConvention {@code true} enables the
	 *                                                check
	 */
	void setCheckProduceEventsFieldNamingConvention(final boolean checkProduceEventsFieldNamingConvention);

	/**
	 * Reports whether produced events field naming convention checks are enabled.
	 *
	 * @return {@code true} when the check is enabled
	 */
	boolean isCheckProduceEventsFieldNamingConvention();

	/**
	 * Enables or disables used subscriber field naming convention checks.
	 *
	 * @param checkUseSubscriberFieldNamingConvention {@code true} enables the check
	 */
	void setCheckUseSubscriberFieldNamingConvention(final boolean checkUseSubscriberFieldNamingConvention);

	/**
	 * Reports whether used subscriber field naming convention checks are enabled.
	 *
	 * @return {@code true} when the check is enabled
	 */
	boolean isCheckUseSubscriberFieldNamingConvention();

	/**
	 * Enables or disables event method naming convention checks.
	 *
	 * @param checkEventsMethodNamingConvention {@code true} enables the check
	 */
	void setCheckEventsMethodNamingConvention(final boolean checkEventsMethodNamingConvention);

	/**
	 * Reports whether event method naming convention checks are enabled.
	 *
	 * @return {@code true} when the check is enabled
	 */
	boolean isCheckEventsMethodNamingConvention();

	/**
	 * Enables or disables used services field naming convention checks.
	 *
	 * @param checkUseServicesFieldNamingConvention {@code true} enables the check
	 */
	void setCheckUseServicesFieldNamingConvention(final boolean checkUseServicesFieldNamingConvention);

	/**
	 * Reports whether used services field naming convention checks are enabled.
	 *
	 * @return {@code true} when the check is enabled
	 */
	boolean isCheckUseServicesFieldNamingConvention();

	/**
	 * Enables or disables accessed parameters field naming convention checks.
	 *
	 * @param checkAccessedParametersFieldNamingConvention {@code true} enables the
	 *                                                     check
	 */
	void setCheckAccessedParametersFieldNamingConvention(final boolean checkAccessedParametersFieldNamingConvention);

	/**
	 * Reports whether accessed parameters field naming convention checks are enabled.
	 *
	 * @return {@code true} when the check is enabled
	 */
	boolean isCheckAccessedParametersFieldNamingConvention();

}

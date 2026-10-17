/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.helloworld;

import org.opales.covalent.container.component.annotations.Component;

/**
 * Component type for the minimal smoke-test demo used to validate the
 * launcher's per-demo container lifecycle (see {@code LauncherApplication}):
 * opens its own window on activation, closes it on passivation, and can be
 * stopped and relaunched independently of every other demo.
 */
@Component
public interface HelloWorldDemo {

}

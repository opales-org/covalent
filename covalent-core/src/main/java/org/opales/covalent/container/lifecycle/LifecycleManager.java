/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.lifecycle;

import org.opales.covalent.container.component.annotations.Component;
import org.opales.covalent.container.component.internals.Internal;

/**
 * <p>
 * Component responsible for management of the components lifecycle.
 * </p>
 * 
 * <p>
 * The container handles the following lifecycle states: deployed,
 * instantiated, initialized, activated, passivated, removed. The regular usage
 * consists in chaining those states from deployment to activation on component
 * manager startup, and then from passivation to undeployment on component
 * manager stop. When the component has been undeployed, it is not managed
 * anymore by the components manager, so there is no associated state.
 * </p>
 * 
 * <p>
 * Each state is associated with a corresponding transition, i.e. deployment,
 * instantiation, initialization, activation, passivation, removal. A last
 * transition, undeployment is added as a last transition when the component is
 * destroyed.
 * </p>
 * 
 * <p>
 * The semantics associated with each transitions is described below.
 * </p>
 * 
 * <ul>
 * <li>Instantiation : the component classes (one for monolithic components,
 * several for segmented component with items) are instantiated</li>
 * <li>Initialization: the component is prepared to be usable.</li>
 * <li>Activation: the component ports are published and ready to be used by
 * others components. The component can be activated several time in its
 * lifetime (activation and passivation are the only lifecycle states which are
 * allowed to be reached more than once)</li>
 * <li>Passivation: the component ports are unpublished and not available
 * anymore from others components. The component can be activated again after
 * passivation.</li>
 * <li>Removal: the resources used by the component must be relaxed, and the
 * component ready to be destroyed. Its provided ports are not usable anymore
 * and will never be again (unless the component is deployed and started
 * again).</li>
 * <li>Undeployment: the component instances are removed from registry.</li>
 * </ul>
 * 
 * The component can implement callbacks for main states (init, activate,
 * passivate, remove), in order for the component to process various tasks by
 * itself. The callback method can be specified either by:
 * <ul>
 * <li>Tagging the method with the corresponding annotation (for
 * instance @Initialization for initialization callback)</li>
 * <li>Implementing one of the lifecycle interface (for instance, Removable for
 * removal)</li>
 * <li>Simply implementing a method by naming convention (the parameter
 * findLifecycleMethodByNameConvention must be set to true, then the method
 * which can be used by naming convention are "initializeComponent",
 * "activateComponent", "passivateComponent" and "removeComponent").</li>
 * </ul>
 * 
 * Some tasks are performed by the container just before, or just after
 * the callback is called. For instance, pre-activation tasks are made just
 * before activation callback is called, and post-activation just after.
 * 
 * <ul>
 * <li>Initialization: The component can start using ports provided by others
 * components, provided that they are already activated. If some steps needed to
 * prepare the component take a lot of time, they must be done during
 * initialization, which is performed only once in the lifetime of the
 * component. Typical operations consists in configuring the component for its
 * current usage and preparing / reserving used resources</li>
 * <li>Activation: the operations performed in activation callback should be as
 * simple and fast as possible in order for activation to be very fast. Typical
 * operations include manual subscription to events producer.</li>
 * <li>Passivation: like activation, this operation should be as fast as
 * possible.</li>
 * <li>Removal: after this stage, the component should not have anymore
 * resources used or reserved.</li>
 * </ul>
 * 
 * <p>
 * The container can also host raw components, of which lifecycle is not
 * managed by the container. the register methods allow to register raw
 * component previously instantiated from outside the container. The components
 * manager will do its best to host the raw component as any managed component,
 * i.e. by instrospecting and extracting its internal structure and managed its
 * ports during its lifecycle.
 * </p>
 * 
 * <p>
 * The lifecycle manager proposes two different start modes : vertical or
 * horizontal. In vertical mode, each component are fully started one after the
 * other, going through all the lifecycle states : instantiate, initialize,
 * activate. In horizontal mode, the lifecycle states are reached by all
 * components before going to next state: all component are instantiated, then
 * initialized, and then, they are all activated.
 * 
 * Both modes may be useful depending on specific application needs. However, it
 * should be noted that horizontal mode does not allow to use services and
 * subscribes from others components during initialization callback, as these
 * others components are themselves in the initialization transition.
 * 
 * In any case, the fine grained lifecycle method allow to handle any specific
 * application needs. For instance, some components may be started first, then
 * the remaining components can be started with horizontal start mode.
 * 
 * The lifecycle methods are smart. For instance, the #activateComponent will
 * first check whether the component has been initialized ; if not the
 * #initializeComponent is called first. Likewise, the #initializeComponent
 * method will first check whether the component has been instantiated, and the
 * #instantiateComponent first check whether the component has been deployed.
 * 
 * Covalent can also reduce startup-order coupling between components. For
 * injected used services, accessed parameters, and {@code @UsedEventsSubscribe}
 * ports, a surrogate can be injected instead of the real component port when
 * the provider has not published its port yet. For services and parameters, the
 * surrogate looks up the real port and replaces itself in the component user's
 * field when the dependency is later used.
 *
 * Automatic event subscriptions and direct raw subscriptions have an additional
 * startup-order rule: if the producer is not published yet, the consumer is
 * registered as pending and automatically subscribed later when the producer is
 * ready. A manually injected {@code @UsedEventsSubscribe} port uses a surrogate
 * for a different reason: the surrogate makes the subscribe port injectable
 * before the producer-side subscribe proxy is published, while the actual
 * subscription still goes through the real proxy once it is available.
 * 
 * <h2>Port lifecycle management (vertical mode)</h2>
 * <p>
 * In vertical start mode (see below), the management of the ports during the
 * component lifecycle is as follows:
 * </p>
 * <ul>
 * <li>Provided services are started in three steps:
 * <ul>
 * <li>Creation of a proxy (post-init)</li>
 * <li>Activation of the port (post-activate)</li>
 * <li>Publication (binding) of the services interface in the ports directory
 * (post-activate)</li>
 * </ul>
 * The port is unpublished (unbound) during pre-passivate, passivated during
 * post-passivate (services users will get an exception if trying to use them),
 * the proxy is invalidated and removed during pre-remove (there may still be
 * references to it from used services).</li>
 * <li>Produced events are started in four steps:
 * <ul>
 * <li>Creation of a producer/subscriber proxy (post-init)</li>
 * <li>Injection of the producer field (post-init)</li>
 * <li>Activation of the port (post-activate)</li>
 * <li>Publication (binding) of the subscribe interface in the ports directory
 * (post-activate).</li>
 * </ul>
 * The port is unpublished (unbound) during pre-passivate, passivated during
 * post-passivate, the proxy is invalidated and removed during pre-remove (there
 * may still exist references to it from used subscribers). It is allowed to
 * subscribe or unsubscribe from a passivated component. However it will not
 * fire events until activated again.</li>
 * <li>Used services are started in three steps:
 * <ul>
 * <li>Lookup of the services provider (pre-init)</li>
 * <li>Creation of a user-side proxy (pre-init)</li>
 * <li>Injection of the used services field (pre-init)</li>
 * </ul>
 * The port is invalidated during pre-remove (using the services will throw an
 * exception).</li>
 * <li>Consumed events with automatic subscribe (i.e., the consumer does not
 * implement the subscribe interface) are started in one step:
 * <ul>
 * <li>Subscribe to the producer (pre-init)</li>
 * </ul>
 * A consumer-side proxy is created on auto-subscribe (in subscriber-side proxy,
 * whether automatically or manually). During pre-remove, the consumed events
 * are unsubscribed and the proxy is removed.</li>
 * <li>Used subscribers are started in three steps similarly to used services:
 * <ul>
 * <li>Lookup of the subscriber (pre-init)</li>
 * <li>Creation of a subscriber-side proxy (pre-init)</li>
 * <li>Injection in the subscriber field (pre-init)</li>
 * </ul>
 * When a consumer subscribes, a consumer-side proxy is created in the
 * subscriber-side proxy (this is true also for automatic subscribe, hence the
 * producer-side proxy never sees the real consumer instance). The port is
 * invalidated during pre-remove.</li>
 * </ul>
 * 
 */
@Internal
@Component
public interface LifecycleManager {

}

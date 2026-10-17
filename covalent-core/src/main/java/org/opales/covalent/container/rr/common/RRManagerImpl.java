/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.rr.common;

import org.opales.covalent.container.util.ShutdownResidues;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.logging.Logger;

import org.opales.covalent.container.component.annotations.UsedServices;
import org.opales.covalent.container.directory.NamingServices;
import org.opales.covalent.container.introspection.IntrospectionServices;
import org.opales.covalent.container.invocation.CovalentInvocationHandler;
import org.opales.covalent.container.metamodel.MetamodelPortReadServices;
import org.opales.covalent.container.registry.PortRegistryServices;
import org.opales.covalent.container.rr.common.RRProxyFactory.RRSurrogateProxyInvocationHandler;
import org.opales.covalent.container.util.RuntimeCheck;
import org.opales.covalent.container.validation.ValidationServices;

/**
 * Base class for the request/response ({@code RR}) flavor-specific
 * managers (one for services, one for parameters): tracks which component
 * provides which interface, which components use it, and owns the
 * provider-side and client-side dynamic proxies backing those ports.
 * Concrete subclasses supply flavor-specific proxy creation, directory
 * lookup, and notification behavior via the abstract methods below.
 */
public abstract class RRManagerImpl {
	/** Logger used to trace surrogate-proxy fallback and other diagnostics. */
	private static Logger log = Logger.getLogger(RRManagerImpl.class.getName());

	/** Internal service used for dynamic verification of usage rules. */
	@UsedServices
	protected ValidationServices validationServices;

	/** Internal service used to read the metamodel's port information. */
	@UsedServices
	protected MetamodelPortReadServices MetamodelPortReadServices;

	/** Internal service used to resolve component naming. */
	@UsedServices
	protected NamingServices namingServices;

	/** The collaborating lifecycle manager for this flavor. */
	private RRLifecycleManagerImpl rrLifecycleManagerImpl;

	/** The collaborating factory used to create provider-side, client-side, and surrogate proxies. */
	private RRProxyFactory rrProxyFactory;

	/** Component type -> the set of interfaces it provides. */
	protected final Map<Class<?>, Set<Class<?>>> componentTypeToProvided = new ConcurrentHashMap<>();

	/** Provided interface -> the set of component types using it. */
	protected final Map<Class<?>, Set<Class<?>>> providedToClients = new ConcurrentHashMap<>();

	/** Provided interface -> its provider-side dynamic proxy. */
	protected final Map<Class<?>, Object> providerSideProxies = new ConcurrentHashMap<>();

	/** Provided interface -> (client component type -> its client-side dynamic proxy). */
	protected final Map<Class<?>, Map<Class<?>, Object>> clientSideProxies = new ConcurrentHashMap<>();

	/** "services" or "parameters", used in log and error messages. */
	private final String kind;

	/**
	 * Creates a new manager for the given request/response flavor.
	 *
	 * @param kind a human-readable port kind ("services" or "parameters"),
	 *             used in log and error messages
	 */
	public RRManagerImpl(final String kind) {
		this.kind = kind;
	}

	/**
	 * Sets the internal service used for dynamic verification of usage
	 * rules.
	 *
	 * @param validationServices the validation service
	 */
	public void setValidationServices(final ValidationServices validationServices) {
		this.validationServices = validationServices;
	}

	/**
	 * Sets the internal service used to read the metamodel's port
	 * information, and propagates it to the collaborating lifecycle manager
	 * and proxy factory.
	 *
	 * @param MetamodelPortReadServices the metamodel port read service
	 */
	public void setMetamodelPortReadServices(final MetamodelPortReadServices MetamodelPortReadServices) {
		this.MetamodelPortReadServices = MetamodelPortReadServices;
		this.rrLifecycleManagerImpl.setMetamodelPortReadServices(MetamodelPortReadServices);
		this.rrProxyFactory.setMetamodelPortReadServices(MetamodelPortReadServices);
	}

	/**
	 * Sets the internal service used for introspection, propagated to the
	 * proxy factory.
	 *
	 * @param introspectionServices the introspection service
	 */
	public void setIntrospectionServices(final IntrospectionServices introspectionServices) {
		this.rrProxyFactory.setIntrospectionServices(introspectionServices);
	}

	/**
	 * Sets the internal service used to look up ports in the registry,
	 * propagated to the proxy factory.
	 *
	 * @param portRegistryServices the port registry service
	 */
	public void setPortRegistryServices(final PortRegistryServices portRegistryServices) {
		this.rrProxyFactory.setPortRegistryServices(portRegistryServices);
	}

	/**
	 * Sets the collaborating proxy factory used to create provider-side,
	 * client-side, and surrogate proxies.
	 *
	 * @param rrProxyFactory the proxy factory
	 */
	public void setProxyFactory(final RRProxyFactory rrProxyFactory) {
		this.rrProxyFactory = rrProxyFactory;
	}

	/**
	 * Sets the collaborating lifecycle manager for this flavor.
	 *
	 * @param rrLifecycleManagerImpl the lifecycle manager
	 */
	public void setLifecycleManager(final RRLifecycleManagerImpl rrLifecycleManagerImpl) {
		this.rrLifecycleManagerImpl = rrLifecycleManagerImpl;
	}

	/**
	 * Sets the internal service used to resolve component naming,
	 * propagated to the collaborating lifecycle manager.
	 *
	 * @param namingServices the naming service
	 */
	public void setNamingServices(final NamingServices namingServices) {
		this.rrLifecycleManagerImpl.setNamingServices(namingServices);
		this.namingServices = namingServices;
	}

	/**
	 * Sets the supplier consulted by created proxies to decide whether
	 * instrumentation callbacks should fire, propagated to the proxy
	 * factory.
	 *
	 * @param instrumentationSupplier supplies whether instrumentation is
	 *                                currently enabled
	 */
	public void setInstrumentationSupplier(final BooleanSupplier instrumentationSupplier) {
		this.rrProxyFactory.setInstrumentationSupplier(instrumentationSupplier);
	}

	/**
	 * Sets the supplier consulted by created provider-side proxies to decide
	 * whether their owning container has been disposed, propagated to the
	 * proxy factory. See {@link ServerRRInvocationHandler} for how it is
	 * used.
	 *
	 * @param disposedSupplier supplies whether the owning container is
	 *                         currently disposed
	 */
	public void setDisposedSupplier(final BooleanSupplier disposedSupplier) {
		this.rrProxyFactory.setDisposedSupplier(disposedSupplier);
	}

	/**
	 * True if the given component type currently uses the given interface.
	 *
	 * @param componentType the candidate client component type
	 * @param anInterface   the services or parameters interface
	 * @return {@code true} if a client-side proxy exists for that pair
	 */
	protected boolean isUsed(final Class<?> componentType, final Class<?> anInterface) {
		Map<Class<?>, Object> clients = this.clientSideProxies.get(anInterface);
		return clients != null && clients.containsKey(componentType);
	}

	/**
	 * True if some component currently provides the given interface.
	 *
	 * @param anInterface the services or parameters interface
	 * @return {@code true} if a provider-side proxy exists for it
	 */
	protected boolean isProvided(final Class<?> anInterface) {
		return this.providerSideProxies.containsKey(anInterface);
	}

	/**
	 * Returns the provided-interface-to-clients map, as a snapshot: a
	 * defensive deep copy (fresh outer map, fresh inner sets), so neither
	 * mutating the returned map or one of its sets nor a later internal
	 * change can affect the other.
	 *
	 * @return a snapshot of the provided-to-clients map
	 */
	protected Map<Class<?>, Set<Class<?>>> getProvidedToClients() {
		return RRManagerImpl.deepCopy(this.providedToClients);
	}

	/**
	 * Returns the component-type-to-provided-interfaces map, as a snapshot:
	 * a defensive deep copy (fresh outer map, fresh inner sets), so neither
	 * mutating the returned map or one of its sets nor a later internal
	 * change can affect the other.
	 *
	 * @return a snapshot of the component-type-to-provided map
	 */
	public Map<Class<?>, Set<Class<?>>> getComponentTypeToProvided() {
		return RRManagerImpl.deepCopy(this.componentTypeToProvided);
	}

	/**
	 * Builds a defensive deep copy of a {@code Class -> Set<Class>}
	 * bookkeeping map: a fresh outer map holding a fresh inner set per
	 * entry, so the copy shares no mutable state with the original.
	 *
	 * @param original the map to copy
	 * @return a deep copy of the given map
	 */
	private static Map<Class<?>, Set<Class<?>>> deepCopy(final Map<Class<?>, Set<Class<?>>> original) {
		Map<Class<?>, Set<Class<?>>> copy = new HashMap<>();
		for (Map.Entry<Class<?>, Set<Class<?>>> entry : original.entrySet()) {
			copy.put(entry.getKey(), new HashSet<>(entry.getValue()));
		}
		return copy;
	}

	/**
	 * Returns the existing provider-side proxy for the given interface, or
	 * creates and registers one on first request.
	 *
	 * @param <RR>        the request/response interface type
	 * @param rrInterface the services or parameters interface
	 * @return the provider-side proxy for that interface
	 */
	protected <RR> RR getOrCreateProxy(final Class<RR> rrInterface) {
		RR proxy = null;
		if (this.providerSideProxies.containsKey(rrInterface)) {
			@SuppressWarnings("unchecked")
			RR proxyInstance = (RR) this.providerSideProxies.get(rrInterface);
			proxy = proxyInstance;
		} else {
			proxy = this.newProxy(rrInterface);
			this.providerSideProxies.put(rrInterface, proxy);
		}

		return proxy;
	}

	/**
	 * Activates the provider-side proxy for the given interface.
	 *
	 * @param <RR>        the request/response interface type
	 * @param rrInterface the provided interface to activate
	 */
	public <RR> void activateRR(final Class<RR> rrInterface) {
		Object proxyInstance = this.providerSideProxies.get(rrInterface);
		@SuppressWarnings("unchecked")
		ServerRRInvocationHandler<RR> invocationHandler = (ServerRRInvocationHandler<RR>) Proxy
				.getInvocationHandler(proxyInstance);
		invocationHandler.activate();
	}

	/**
	 * True if the provider-side proxy for the given interface is currently
	 * activated.
	 *
	 * @param <RR>        the request/response interface type
	 * @param rrInterface the provided interface to check
	 * @return {@code true} if its provider-side proxy is activated
	 */
	public <RR> boolean isActivatedRR(final Class<RR> rrInterface) {
		Object proxyInstance = this.providerSideProxies.get(rrInterface);
		@SuppressWarnings("unchecked")
		ServerRRInvocationHandler<RR> invocationHandler = (ServerRRInvocationHandler<RR>) Proxy
				.getInvocationHandler(proxyInstance);
		return invocationHandler.isActivated();
	}

	/**
	 * Passivates the provider-side proxy for the given interface.
	 *
	 * @param <RR>        the request/response interface type
	 * @param rrInterface the provided interface to passivate
	 */
	public <RR> void passivateRR(final Class<RR> rrInterface) {
		Object proxyInstance = this.providerSideProxies.get(rrInterface);
		@SuppressWarnings("unchecked")
		ServerRRInvocationHandler<RR> invocationHandler = (ServerRRInvocationHandler<RR>) Proxy
				.getInvocationHandler(proxyInstance);
		invocationHandler.passivate();
	}

	/**
	 * Declares that the given component type provides the given interface,
	 * creating its provider-side proxy if this is the first provider. A
	 * duplicate declaration for the same interface is reported through
	 * {@link ValidationServices#handleDynamicVerification}, not thrown.
	 *
	 * @param <RR>          the request/response interface type
	 * @param componentType the providing component type
	 * @param rrInterface   the interface it provides; must not be
	 *                      {@code null}
	 */
	public <RR> void createProvidedRR(final Class<?> componentType, final Class<RR> rrInterface) {
		RuntimeCheck.notNull(rrInterface, "rrInterface");

		Set<Class<?>> set = this.componentTypeToProvided.computeIfAbsent(componentType,
				key -> ConcurrentHashMap.newKeySet());
		if (set.contains(rrInterface)) {
			String message = this.kind + " " + rrInterface + " already provided for component " + componentType;
			this.validationServices.handleDynamicVerification(message);
		}

		set.add(rrInterface);

		this.getOrCreateProxy(rrInterface);

		this.notifyProvided(componentType, rrInterface);
	}

	/**
	 * Returns the provider-side proxy for the given interface.
	 *
	 * @param <RR>        the request/response interface type
	 * @param rrInterface the provided interface; must not be {@code null}
	 *                    and must already be provided
	 * @return the provider-side proxy for that interface
	 */
	public <RR> RR getProvider(final Class<RR> rrInterface) {
		RuntimeCheck.notNull(rrInterface, "rrInterface");
		RuntimeCheck.isTrue(this.providerSideProxies.containsKey(rrInterface),
				"Provided " + this.kind.toLowerCase() + " is known");
		@SuppressWarnings("unchecked")
		RR rrProvider = (RR) this.providerSideProxies.get(rrInterface);
		return rrProvider;
	}

	/**
	 * Removes the given interface's provider, invalidating its provider-side
	 * proxy. The client-side proxies that were connected to it are not
	 * invalidated: each one is put back on a surrogate, so that it reaches the
	 * next provider of the interface as soon as there is one, and fails with a
	 * clear message until then. A missing provider is reported through
	 * {@link ValidationServices#handleDynamicVerification}, not thrown.
	 *
	 * @param <RR>        the request/response interface type
	 * @param rrInterface the provided interface to remove; must not be
	 *                    {@code null}
	 */
	public <RR> void deleteProvider(final Class<RR> rrInterface) {
		RuntimeCheck.notNull(rrInterface, "rrInterface");
		Class<?> componentType = this.getComponentTypeForProvider(rrInterface);

		if (componentType != null) {
			Set<Class<?>> set = this.componentTypeToProvided.getOrDefault(componentType, Collections.emptySet());
			set.remove(rrInterface);
			if (set.isEmpty()) {
				this.componentTypeToProvided.remove(componentType);
			}
		}

		Object proxy = this.providerSideProxies.get(rrInterface);
		if (proxy == null) {
			String message = this.kind + " not provided";
			this.validationServices.handleDynamicVerification(message);
			return;
		}
		CovalentInvocationHandler invocationHandler = (CovalentInvocationHandler) Proxy.getInvocationHandler(proxy);
		invocationHandler.invalidate();

		this.providerSideProxies.remove(rrInterface);
		this.rebindAllClientProxiesToSurrogates(rrInterface);

		this.notifyUnprovided(componentType, rrInterface);
	}

	/**
	 * Detaches every client-side proxy currently connected to the given
	 * interface from its (removed) provider and puts it back on a new
	 * surrogate. The usage records are kept: a component using a port keeps
	 * using it when the provider goes away, and the surrogate resolves the
	 * next provider from the directory at the first call after it arrives.
	 *
	 * @param <RR>        the request/response interface type
	 * @param rrInterface the provided interface whose clients are detached
	 */
	private <RR> void rebindAllClientProxiesToSurrogates(final Class<RR> rrInterface) {
		Map<Class<?>, Object> proxiesByClient = this.clientSideProxies.get(rrInterface);
		if (proxiesByClient == null) {
			return;
		}
		for (Object clientProxy : new ArrayList<>(proxiesByClient.values())) {
			@SuppressWarnings("unchecked")
			ClientRRInvocationHandler<RR> clientHandler = (ClientRRInvocationHandler<RR>) Proxy
					.getInvocationHandler(clientProxy);
			RR surrogate = this.newSurrogateProxy(rrInterface);
			@SuppressWarnings("unchecked")
			RRSurrogateProxyInvocationHandler<RR> surrogateHandler = (RRSurrogateProxyInvocationHandler<RR>) Proxy
					.getInvocationHandler(surrogate);
			surrogateHandler.setClientRRInvocationHandler(clientHandler);
			clientHandler.replaceClientRR(surrogate);
		}
	}

	/**
	 * Registers the given component type as a client of the given interface
	 * and returns its client-side proxy, creating a surrogate proxy first if
	 * no provider is currently declared. A duplicate registration for the
	 * same client/interface pair is reported through
	 * {@link ValidationServices#handleDynamicVerification}, and the existing
	 * proxy is returned as-is rather than reconnected - see
	 * {@link #reconnectClientProxy} for the lifecycle-internal variant that
	 * tolerates this instead.
	 *
	 * @param <RR>               the request/response interface type
	 * @param clientComponentType the client component type
	 * @param rrInterface         the interface to use
	 * @return the client-side proxy for that pair
	 */
	public <RR> RR createClientProxy(final Class<?> clientComponentType, final Class<RR> rrInterface) {
		Set<Class<?>> clients = this.providedToClients.getOrDefault(rrInterface, Collections.emptySet());
		if (clients.contains(clientComponentType)) {
			String message = this.kind + " " + rrInterface + " already used by component " + clientComponentType;
			this.validationServices.handleDynamicVerification(message);
			@SuppressWarnings("unchecked")
			RR existingProxy = (RR) this.clientSideProxies.getOrDefault(rrInterface, Collections.emptyMap())
					.get(clientComponentType);
			return existingProxy;
		}
		return this.connectClientProxy(clientComponentType, rrInterface);
	}

	/**
	 * Same as {@link #createClientProxy(Class, Class)}, except that a client
	 * already registered is tolerated instead of reported as a duplicate
	 * usage: the existing proxy is simply handed back.
	 * <p>
	 * This is for the lifecycle machinery only, not for application code:
	 * used services/subscribers are guaranteed to stay functioning across a
	 * passivate/activate cycle (see
	 * {@code LifecycleManagerImpl#passivateComponent}'s javadoc), so they are
	 * never torn down on passivation - only on removal. A component that
	 * alternates activate/passivate without being removed (e.g. horizontal
	 * mode, which (re)creates its used ports at every preActivate) will
	 * legitimately ask for the same client registration again while the
	 * previous one is still live; that is not a duplicate-usage mistake made
	 * by application code, and must not be reported as a dynamic
	 * verification failure the way {@link #createClientProxy(Class, Class)}
	 * still correctly does for an actual duplicate manual use.
	 *
	 * @param <RR>               the request/response interface type
	 * @param clientComponentType the client component type
	 * @param rrInterface         the interface to use
	 * @return the client-side proxy for that pair, existing or newly created
	 */
	public <RR> RR reconnectClientProxy(final Class<?> clientComponentType, final Class<RR> rrInterface) {
		Set<Class<?>> clients = this.providedToClients.getOrDefault(rrInterface, Collections.emptySet());
		if (clients.contains(clientComponentType)) {
			@SuppressWarnings("unchecked")
			RR existingProxy = (RR) this.clientSideProxies.getOrDefault(rrInterface, Collections.emptyMap())
					.get(clientComponentType);
			return existingProxy;
		}
		return this.connectClientProxy(clientComponentType, rrInterface);
	}

	/**
	 * Creates and registers a client-side proxy for the given component type
	 * and interface, looking up the real provider-side proxy if one exists
	 * or creating a temporary surrogate proxy otherwise.
	 *
	 * @param <RR>               the request/response interface type
	 * @param clientComponentType the client component type
	 * @param rrInterface         the interface to use
	 * @return the newly created client-side proxy
	 */
	private <RR> RR connectClientProxy(final Class<?> clientComponentType, final Class<RR> rrInterface) {
		this.providedToClients.compute(rrInterface, (key, clients) -> {
			Set<Class<?>> updated = clients == null ? ConcurrentHashMap.newKeySet() : clients;
			updated.add(clientComponentType);
			return updated;
		});
		// lookup provider-side proxy
		RR providerProxy = null;
		boolean useSurrogate = false;
		if (this.isBound(rrInterface)) {
			providerProxy = this.lookup(rrInterface);
		} else {
			// temporarily replace missing provider-side proxy with surrogate proxy
			log.fine("Provided " + this.kind.toLowerCase() + " not published in directory. Using surrogate instead");
			providerProxy = this.newSurrogateProxy(rrInterface);
			useSurrogate = true;
		}

		// create user-side proxy
		RR clientProxy = this.newClientProxy(clientComponentType, rrInterface, providerProxy);

		if (useSurrogate) {
			@SuppressWarnings("unchecked")
			RRSurrogateProxyInvocationHandler<RR> surrogateInvocationHandler = (RRSurrogateProxyInvocationHandler<RR>) Proxy
					.getInvocationHandler(providerProxy);
			@SuppressWarnings("unchecked")
			ClientRRInvocationHandler<RR> clientSideInvocationHandler = (ClientRRInvocationHandler<RR>) Proxy
					.getInvocationHandler(clientProxy);
			surrogateInvocationHandler.setClientRRInvocationHandler(clientSideInvocationHandler);
		}

		// store interface -> client -> proxy
		this.clientSideProxies.computeIfAbsent(rrInterface, key -> new ConcurrentHashMap<>()).put(clientComponentType,
				clientProxy);

		this.notifyUsed(clientComponentType, rrInterface);

		return clientProxy;
	}

	/**
	 * Unregisters the given component type as a client of the given
	 * interface, invalidating its client-side proxy. A client not currently
	 * registered is reported through
	 * {@link ValidationServices#handleDynamicVerification}, not thrown.
	 *
	 * @param <RR>               the request/response interface type
	 * @param clientComponentType the client component type
	 * @param rrInterface         the interface it stops using
	 */
	protected <RR> void deleteClientProxy(final Class<?> clientComponentType, final Class<RR> rrInterface) {
		Set<Class<?>> set = this.providedToClients.getOrDefault(rrInterface, Collections.emptySet());
		if (!set.contains(clientComponentType)) {
			String message = this.kind + " " + rrInterface + " not used by component " + clientComponentType;
			this.validationServices.handleDynamicVerification(message);
			return;
		}
		// an interface left without any client is no longer listed
		this.providedToClients.computeIfPresent(rrInterface, (key, clients) -> {
			clients.remove(clientComponentType);
			return clients.isEmpty() ? null : clients;
		});

		// remove interface -> client -> proxy
		Map<Class<?>, Object> proxiesByClient = this.clientSideProxies.get(rrInterface);
		@SuppressWarnings("unchecked")
		RR usedServices = (RR) proxiesByClient.remove(clientComponentType);
		// clean if no more proxies for this interface
		if (proxiesByClient.isEmpty()) {
			this.clientSideProxies.remove(rrInterface);
		}

		@SuppressWarnings("unchecked")
		ClientRRInvocationHandler<RR> invocationHandler = (ClientRRInvocationHandler<RR>) Proxy
				.getInvocationHandler(usedServices);
		invocationHandler.invalidate();

		this.notifyUnused(clientComponentType, rrInterface);

	}

	/**
	 * Lists what is left behind once the business components are torn down: every
	 * entry describes something that is not an internal component. Run by the
	 * container's {@code stop()}, which reports the result in the shutdown report.
	 *
	 * @return the residues found, empty when only internal components remain
	 */
	public List<String> listNonInternalResidues() {
		List<String> residues = new ArrayList<>();
		int providedInterfaces = 0;
		for (Map.Entry<Class<?>, Set<Class<?>>> entry : this.componentTypeToProvided.entrySet()) {
			ShutdownResidues.addIfNotInternal(residues, this.kind, "provider still registered", entry.getKey());
			providedInterfaces += entry.getValue().size();
		}
		if (this.providerSideProxies.size() != providedInterfaces) {
			ShutdownResidues.add(residues, this.kind, "provider-side proxies (" + this.providerSideProxies.size()
					+ ") do not match the provided interfaces (" + providedInterfaces + ")");
		}
		int clientRegistrations = 0;
		for (Set<Class<?>> clients : this.providedToClients.values()) {
			for (Class<?> client : clients) {
				ShutdownResidues.addIfNotInternal(residues, this.kind, "client still registered", client);
				clientRegistrations++;
			}
		}
		int clientSideProxyCount = 0;
		for (Map<Class<?>, Object> proxiesByClient : this.clientSideProxies.values()) {
			clientSideProxyCount += proxiesByClient.size();
		}
		if (clientSideProxyCount != clientRegistrations) {
			ShutdownResidues.add(residues, this.kind, "client-side proxies (" + clientSideProxyCount
					+ ") do not match the client registrations (" + clientRegistrations + ")");
		}
		return residues;
	}


	/**
	 * Creates a new provider-side invocation handler proxy for the given
	 * interface.
	 *
	 * @param <RR>              the request/response interface type
	 * @param servicesInterface the interface to create a proxy for
	 * @return the new provider-side proxy
	 */
	protected abstract <RR> RR newProxy(final Class<RR> servicesInterface);

	/**
	 * Notifies flavor-specific collaborators (typically the directory) that
	 * the given component type now provides the given interface.
	 *
	 * @param <RR>          the request/response interface type
	 * @param componentType the providing component type
	 * @param rrInterface   the newly provided interface
	 */
	protected abstract <RR> void notifyProvided(final Class<?> componentType, final Class<RR> rrInterface);

	/**
	 * Notifies flavor-specific collaborators that the given component type
	 * no longer provides the given interface.
	 *
	 * @param <RR>          the request/response interface type
	 * @param componentType the component type that stopped providing it
	 * @param rrInterface   the interface no longer provided
	 */
	protected abstract <RR> void notifyUnprovided(final Class<?> componentType, final Class<RR> rrInterface);

	/**
	 * Resolves the component type currently declared as provider for the
	 * given interface.
	 *
	 * @param <RR>        the request/response interface type
	 * @param rrInterface the provided interface
	 * @return the providing component type
	 */
	protected abstract <RR> Class<?> getComponentTypeForProvider(Class<RR> rrInterface);

	/**
	 * True if the given interface currently has a provider bound in the
	 * directory.
	 *
	 * @param <RR>        the request/response interface type
	 * @param rrInterface the interface to check
	 * @return {@code true} if bound to a provider
	 */
	protected abstract <RR> boolean isBound(final Class<RR> rrInterface);

	/**
	 * Looks up the current provider-side proxy for the given interface in
	 * the directory.
	 *
	 * @param <RR>        the request/response interface type
	 * @param rrInterface the interface to look up
	 * @return the resolved provider-side proxy
	 */
	protected abstract <RR> RR lookup(final Class<RR> rrInterface);

	/**
	 * Creates a temporary surrogate proxy for the given interface, used
	 * while no real provider is declared yet.
	 *
	 * @param <RR>        the request/response interface type
	 * @param rrInterface the interface to create a surrogate for
	 * @return the new surrogate proxy
	 */
	protected abstract <RR> RR newSurrogateProxy(final Class<RR> rrInterface);

	/**
	 * Creates a new client-side proxy for the given client component type
	 * and interface, forwarding to the given provider-side target.
	 *
	 * @param <RR>               the request/response interface type
	 * @param clientComponentType the client component type
	 * @param rrInterface         the interface to use
	 * @param provided            the provider-side proxy (real or
	 *                            surrogate) to forward calls to
	 * @return the new client-side proxy
	 */
	protected abstract <RR> RR newClientProxy(final Class<?> clientComponentType, final Class<RR> rrInterface,
			final RR provided);

	/**
	 * Notifies flavor-specific collaborators that the given component type
	 * now uses the given interface.
	 *
	 * @param <RR>               the request/response interface type
	 * @param clientComponentType the client component type
	 * @param rrInterface         the newly used interface
	 */
	protected abstract <RR> void notifyUsed(final Class<?> clientComponentType, final Class<RR> rrInterface);

	/**
	 * Notifies flavor-specific collaborators that the given component type
	 * no longer uses the given interface.
	 *
	 * @param <RR>               the request/response interface type
	 * @param clientComponentType the client component type that stopped
	 *                            using it
	 * @param rrInterface         the interface no longer used
	 */
	protected abstract <RR> void notifyUnused(final Class<?> clientComponentType, final Class<RR> rrInterface);

}

/*
 * Copyright (C) 2011 Everit Kft. (http://www.everit.biz)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.everit.authentication.cas;

import java.util.Objects;
import java.util.Optional;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionActivationListener;
import javax.servlet.http.HttpSessionEvent;

/**
 * A {@link HttpSessionActivationListener} that handles the lifecycle of a {@link HttpSession}.
 */
public final class CasHttpSessionActivationListener
    implements HttpSessionActivationListener {

  /**
   * The session attribute name prefix used for the {@link CasHttpSessionActivationListener}
   * instance.
   */
  private static final String SESSION_ATTR_NAME_INSTANCE_PREFIX =
      CasHttpSessionActivationListener.class.getName() + ".instance.";

  /**
   * The session attribute name prefix used for the Service PID.
   */
  public static final String SESSION_ATTR_NAME_SERVICE_PID_PREFIX =
      CasHttpSessionActivationListener.class.getName() + ".servicePid.";

  /**
   * Creates the session attribute name of the {@link CasHttpSessionActivationListener} instance
   * stored in the {@link HttpSession}.
   *
   * @param servicePid
   *          the Service PID of the OSGi service that handles the CAS authentication. Used in the
   *          session attribute name to guarantee its uniqueness.
   * @return the created session attribute name
   */
  public static String createSessionAttrNameInstance(final String servicePid) {
    return SESSION_ATTR_NAME_INSTANCE_PREFIX + servicePid;
  }

  /**
   * Creates the session attribute name of the Service PID stored in the {@link HttpSession}.
   *
   * @param servicePid
   *          the Service PID of the OSGi service that handles the CAS authentication. Stored in the
   *          {@link HttpSession} and used in the session attribute name to guarantee its
   *          uniqueness.
   * @return the created session attribute name
   */
  public static String createSessionAttrNameServicePid(final String servicePid) {
    return SESSION_ATTR_NAME_SERVICE_PID_PREFIX + servicePid;
  }

  /**
   * Registers a new instance of {@link CasHttpSessionActivationListener} to the session with
   * attribute name constructed from the provided Service PID.
   *
   * @param servicePid
   *          the Service PID of the OSGi service that handles the CAS authentication. Used to
   *          construct the session attribute name and assigned to the
   *          {@link CasHttpSessionActivationListener} to handle
   *          {@link #sessionDidActivate(HttpSessionEvent)} and
   *          {@link #sessionWillPassivate(HttpSessionEvent)} events.
   * @param httpSession
   *          the {@link HttpSession} where the new instance is registered
   * @throws IllegalStateException
   *           if an instance is already assigned to the session with the same Service PID
   */
  public static void registerInstance(final String servicePid, final HttpSession httpSession) {
    String sessionAttrName =
        CasHttpSessionActivationListener.createSessionAttrNameInstance(servicePid);
    Optional<Object> optionalInstance =
        Optional.ofNullable(httpSession.getAttribute(sessionAttrName));
    optionalInstance.ifPresent((instance) -> {
      throw new IllegalStateException("HttpSession attribute [" + sessionAttrName + "] "
          + "already registered");
    });
    httpSession.setAttribute(sessionAttrName, new CasHttpSessionActivationListener(servicePid));
  }

  /**
   * Removes the instance of {@link CasHttpSessionActivationListener} from the session that was
   * previously registered with the given Service PID.
   *
   * @param servicePid
   *          the Service PID of the OSGi service that handles the CAS authentication. Used to
   *          construct the session attribute name that identifies the previously registered
   *          {@link CasHttpSessionActivationListener} instance in the session.
   * @param httpSession
   *          the {@link CasHttpSessionActivationListener} will be removed from this session.
   */
  public static void removeInstance(final String servicePid, final HttpSession httpSession) {
    String sessionAttrName =
        CasHttpSessionActivationListener.createSessionAttrNameInstance(servicePid);
    httpSession.removeAttribute(sessionAttrName);
  }

  /**
   * The Service PID of the OSGi service that handles the CAS authentication.
   */
  private final String servicePid;

  /**
   * Private constructor. Use {@link #registerInstance(String, HttpSession)} to instantiate and
   * register this {@link HttpSessionActivationListener} to the session.
   *
   * @param servicePid
   *          the Service PID of the OSGi service that handles the CAS authentication
   */
  private CasHttpSessionActivationListener(final String servicePid) {
    super();
    this.servicePid = Objects.requireNonNull(servicePid, "servicePid cannot be null");
  }

  /**
   * Registers the activated {@link HttpSession} to the {@link CasHttpSessionRegistry}.
   */
  @Override
  public void sessionDidActivate(final HttpSessionEvent httpSessionEvent) {
    HttpSession httpSession = httpSessionEvent.getSession();
    ServletContext servletContext = httpSession.getServletContext();
    CasHttpSessionRegistry.getInstance(servicePid, servletContext).putSession(httpSession);
  }

  /**
   * Removes the passivated {@link HttpSession} from the {@link CasHttpSessionRegistry} and prepares
   * the session serialization. This means it sets the {@link #servicePid} as a session attribute
   * with a dedicated attribute name constructed from the {@link #servicePid} and the
   * {@link HttpSessionAttributeListener} implemented by {@link CasAuthentication} will handle this
   * event.
   *
   * @see CasAuthentication#attributeAdded(javax.servlet.http.HttpSessionBindingEvent)
   */
  @Override
  public void sessionWillPassivate(final HttpSessionEvent httpSessionEvent) {
    HttpSession httpSession = httpSessionEvent.getSession();
    ServletContext servletContext = httpSession.getServletContext();
    CasHttpSessionRegistry.getInstance(servicePid, servletContext).removeBySession(httpSession);
    // prepare to session serialization
    httpSession.setAttribute(
        CasHttpSessionActivationListener.createSessionAttrNameServicePid(servicePid),
        servicePid);
  }

}

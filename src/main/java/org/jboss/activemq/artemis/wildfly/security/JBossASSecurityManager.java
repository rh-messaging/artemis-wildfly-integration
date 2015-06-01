/*
 * JBoss, Home of Professional Open Source
 * Copyright 2005-2008, Red Hat Middleware LLC, and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.activemq.artemis.wildfly.security;

import java.security.AccessController;
import java.security.Principal;
import java.security.PrivilegedAction;
import java.util.HashSet;
import java.util.Set;

import javax.naming.InitialContext;
import javax.security.auth.Subject;

import org.apache.activemq.artemis.core.security.CheckType;
import org.apache.activemq.artemis.core.security.Role;
import org.apache.activemq.artemis.core.server.ActiveMQComponent;
import org.apache.activemq.artemis.spi.core.security.ActiveMQSecurityManager;
import org.jboss.activemq.artemis.wildfly.ActiveMQJBossLogger;
import org.jboss.security.AuthenticationManager;
import org.jboss.security.RealmMapping;
import org.jboss.security.SecurityContext;
import org.jboss.security.SecurityContextAssociation;
import org.jboss.security.SimplePrincipal;

/**
 * This implementation delegates to the JBoss AS security interfaces (which in turn use JAAS)
 * It can be used when running ActiveMQ in JBoss AS
 *
 * @author <a href="ataylor@redhat.com">Andy Taylor</a>
 * @author <a href="tim.fox@jboss.com">Tim Fox</a>
 */
public class JBossASSecurityManager implements ActiveMQSecurityManager, ActiveMQComponent
{
   // Static --------------------------------------------------------

   // Attributes ----------------------------------------------------

   private final boolean trace = ActiveMQJBossLogger.LOGGER.isTraceEnabled();

   /**
    * the realmmapping
    */
   private RealmMapping realmMapping;

   /**
    * the JAAS Authentication Manager
    */
   private AuthenticationManager authenticationManager;

   /**
    * The JNDI name of the AuthenticationManager(and RealmMapping since they are the same object).
    */
   private String securityDomainName = "java:/jaas/activemq";

   private boolean started;

   private boolean isAs5 = true;

   private boolean allowClientLogin = false;

   private boolean authoriseOnClientLogin = false;

   public boolean validateUser(final String user, final String password)
   {
      SimplePrincipal principal = new SimplePrincipal(user);

      char[] passwordChars = null;

      if (password != null)
      {
         passwordChars = password.toCharArray();
      }

      Subject subject = new Subject();

      return authenticationManager.isValid(principal, passwordChars, subject);
   }

   public boolean validateUserAndRole(final String user,
                                      final String password,
                                      final Set<Role> roles,
                                      final CheckType checkType)
   {
      if (allowClientLogin && SecurityContextAssociation.isClient())
      {
         return authoriseOnClientLogin ? useClientAuthentication(roles, checkType) : true;
      }
      else
      {
         return useConnectionAuthentication(user, password, roles, checkType);
      }
   }

   private boolean useConnectionAuthentication(final String user,
                                               final String password,
                                               final Set<Role> roles,
                                               final CheckType checkType)
   {
      return AccessController.doPrivileged(new PrivilegedAction<Boolean>()
      {
         public Boolean run()
         {

            SimplePrincipal principal = user == null ? null : new SimplePrincipal(user);

            char[] passwordChars = null;

            if (password != null)
            {
               passwordChars = password.toCharArray();
            }

            Subject subject = new Subject();

            boolean authenticated = authenticationManager.isValid(principal, passwordChars, subject);
            // Authenticate. Successful authentication will place a new SubjectContext on thread local,
            // which will be used in the authorization process. However, we need to make sure we clean up
            // thread local immediately after we used the information, otherwise some other people
            // security my be screwed up, on account of thread local security stack being corrupted.
            if (authenticated)
            {
               pushSecurityContext(principal, passwordChars, subject);
               Set<Principal> rolePrincipals = getRolePrincipals(checkType, roles);

               authenticated = realmMapping.doesUserHaveRole(principal, rolePrincipals);

               if (trace)
               {
                  ActiveMQJBossLogger.LOGGER.trace("user " + user +
                                                   (authenticated ? " is " : " is NOT ") +
                                                   "authorized");
               }
               popSecurityContext();
            }
            return authenticated;
         }
      });
   }

   private boolean useClientAuthentication(final Set<Role> roles, final CheckType checkType)
   {
      return AccessController.doPrivileged(new PrivilegedAction<Boolean>()
      {
         public Boolean run()
         {
            SecurityContext sc = SecurityContextAssociation.getSecurityContext();
            Principal principal = sc.getUtil().getUserPrincipal();

            char[] passwordChars = (char[])sc.getUtil().getCredential();

            Subject subject = sc.getSubjectInfo().getAuthenticatedSubject();

            boolean authenticated = authenticationManager.isValid(principal, passwordChars, subject);

            if (authenticated)
            {
               Set<Principal> rolePrincipals = getRolePrincipals(checkType, roles);

               authenticated = realmMapping.doesUserHaveRole(principal, rolePrincipals);

               if (trace)
               {
                  ActiveMQJBossLogger.LOGGER.trace("user " + principal.getName() +
                                                   (authenticated ? " is " : " is NOT ") +
                                                   "authorized");
               }
            }
            return authenticated;
         }

      });

   }

   private void popSecurityContext()
   {
      if (isAs5)
      {
         SecurityActions.popSubjectContext();
      }
      else
      {
         AS4SecurityActions.popSubjectContext();
      }
   }

   private void pushSecurityContext(final SimplePrincipal principal, final char[] passwordChars, final Subject subject)
   {
      if (isAs5)
      {
         SecurityActions.pushSubjectContext(principal, passwordChars, subject, securityDomainName);
      }
      else
      {
         AS4SecurityActions.pushSubjectContext(principal, passwordChars, subject);
      }
   }

   public void addRole(final String user, final String role)
   {
      // NO-OP
   }

   public void addUser(final String user, final String password)
   {
      // NO-OP
   }

   public void removeRole(final String user, final String role)
   {
      // NO-OP
   }

   public void removeUser(final String user)
   {
      // NO-OP
   }

   public void setDefaultUser(final String username)
   {
      // NO-OP
   }

   private Set<Principal> getRolePrincipals(final CheckType checkType, final Set<Role> roles)
   {
      Set<Principal> principals = new HashSet<Principal>();
      for (Role role : roles)
      {
         if (checkType.hasRole(role))
         {
            principals.add(new SimplePrincipal(role.getName()));
         }
      }
      return principals;
   }

   public void setRealmMapping(final RealmMapping realmMapping)
   {
      this.realmMapping = realmMapping;
   }

   public void setAuthenticationManager(final AuthenticationManager authenticationManager)
   {
      this.authenticationManager = authenticationManager;
   }

   /**
    * lifecycle method, needs to be called
    *
    * @throws Exception
    */
   public synchronized void start() throws Exception
   {
      if (started)
      {
         return;
      }

      InitialContext ic = new InitialContext();
      authenticationManager = (AuthenticationManager)ic.lookup(securityDomainName);
      realmMapping = (RealmMapping)authenticationManager;

      started = true;
   }

   public synchronized void stop()
   {
      if (!started)
      {
         return;
      }
      started = false;
   }

   public synchronized boolean isStarted()
   {
      return started;
   }

   public void setSecurityDomainName(final String securityDomainName)
   {
      this.securityDomainName = securityDomainName;
   }

   public void setAs5(final boolean as5)
   {
      isAs5 = as5;
   }

   public void setAllowClientLogin(final boolean allowClientLogin)
   {
      this.allowClientLogin = allowClientLogin;
   }

   public void setAuthoriseOnClientLogin(final boolean authoriseOnClientLogin)
   {
      this.authoriseOnClientLogin = authoriseOnClientLogin;
   }
}

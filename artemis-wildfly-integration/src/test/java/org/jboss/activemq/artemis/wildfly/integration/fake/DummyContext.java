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
package org.jboss.activemq.artemis.wildfly.integration.fake;

import javax.naming.*;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;

/**
 * @author <a href="mailto:andy.taylor@jboss.org">Andy Taylor</a>
 */
public class DummyContext implements Context {

    Map<String, Object> objects = new HashMap<>();

    @Override
    public Object lookup(Name name) throws NamingException {
        return null;
    }

    @Override
    public Object lookup(String name) throws NamingException {
        return objects.get(name);
    }

    @Override
    public void bind(Name name, Object obj) throws NamingException {

    }

    @Override
    public void bind(String name, Object obj) throws NamingException {
        objects.put(name, obj);
    }

    @Override
    public void rebind(Name name, Object obj) throws NamingException {
        objects.put(name.toString(), obj);
    }

    @Override
    public void rebind(String name, Object obj) throws NamingException {
        objects.put(name, obj);
    }

    @Override
    public void unbind(Name name) throws NamingException {

    }

    @Override
    public void unbind(String name) throws NamingException {

    }

    @Override
    public void rename(Name oldName, Name newName) throws NamingException {

    }

    @Override
    public void rename(String oldName, String newName) throws NamingException {

    }

    @Override
    public NamingEnumeration<NameClassPair> list(Name name) throws NamingException {
        return null;
    }

    @Override
    public NamingEnumeration<NameClassPair> list(String name) throws NamingException {
        return null;
    }

    @Override
    public NamingEnumeration<Binding> listBindings(Name name) throws NamingException {
        return null;
    }

    @Override
    public NamingEnumeration<Binding> listBindings(String name) throws NamingException {
        return null;
    }

    @Override
    public void destroySubcontext(Name name) throws NamingException {

    }

    @Override
    public void destroySubcontext(String name) throws NamingException {

    }

    @Override
    public Context createSubcontext(Name name) throws NamingException {
        return null;
    }

    @Override
    public Context createSubcontext(String name) throws NamingException {
        return null;
    }

    @Override
    public Object lookupLink(Name name) throws NamingException {
        return null;
    }

    @Override
    public Object lookupLink(String name) throws NamingException {
        return null;
    }

    @Override
    public NameParser getNameParser(Name name) throws NamingException {
        return null;
    }

    @Override
    public NameParser getNameParser(String name) throws NamingException {
        return null;
    }

    @Override
    public Name composeName(Name name, Name prefix) throws NamingException {
        return null;
    }

    @Override
    public String composeName(String name, String prefix) throws NamingException {
        return null;
    }

    @Override
    public Object addToEnvironment(String propName, Object propVal) throws NamingException {
        return null;
    }

    @Override
    public Object removeFromEnvironment(String propName) throws NamingException {
        return null;
    }

    @Override
    public Hashtable<?, ?> getEnvironment() throws NamingException {
        return null;
    }

    @Override
    public void close() throws NamingException {
        this.objects.clear();
    }

    @Override
    public String getNameInNamespace() throws NamingException {
        return null;
    }
}

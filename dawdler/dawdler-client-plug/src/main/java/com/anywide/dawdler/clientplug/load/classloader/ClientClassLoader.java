/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.anywide.dawdler.clientplug.load.classloader;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.ByteBuffer;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.CodeSigner;
import java.security.CodeSource;
import java.security.PrivilegedAction;
import java.security.PrivilegedExceptionAction;
import java.util.jar.Attributes;
import java.util.jar.Attributes.Name;
import java.util.jar.Manifest;

import sun.misc.Resource;
import sun.misc.URLClassPath;

/**
 * @author jackson.song
 * @version V1.0
 * @Title ClientClassLoader.java
 * @Description 客户端的类加载器，主要用来加载远端模版类
 * @date 2007年9月13日
 * @email suxuan696@gmail.com
 */

public class ClientClassLoader extends URLClassLoader {
	private final URLClassPath ucp;
	private AccessControlContext acc;

	@Override
	public URL getResource(String name) {
		Resource resource = ucp.getResource(name);
		if (resource != null) {
			return resource.getURL();
		}
		return super.getResource(name);
	}

	public ClientClassLoader(URL[] urls, java.lang.ClassLoader parent) {
		super(urls, parent);
		SecurityManager security = System.getSecurityManager();
		if (security != null) {
			security.checkCreateClassLoader();
		}
		acc = AccessController.getContext();
		ucp = new URLClassPath(urls);

	}

	public ClientClassLoader(URL[] urls) {
		super(urls);
		SecurityManager security = System.getSecurityManager();
		if (security != null) {
			security.checkCreateClassLoader();
		}
		acc = AccessController.getContext();
		ucp = new URLClassPath(urls);
	}

	public static ClientClassLoader newInstance(final URL[] urls, final java.lang.ClassLoader parent) {
		AccessControlContext acc = AccessController.getContext();
		ClientClassLoader ucl = (ClientClassLoader) AccessController
				.doPrivileged((PrivilegedAction) () -> new FactoryURLClassLoader(urls, parent));
		ucl.acc = acc;
		return ucl;
	}

	@Override
	protected Class<?> findClass(final String name) throws ClassNotFoundException {
		try {
			return (Class<?>) AccessController.doPrivileged((PrivilegedExceptionAction<?>) () -> {
				String path = name.replace('.', '/').concat(".class");
				Resource res = ucp.getResource(path, false);
				if (res != null) {
					try {
						return defineClass(name, res);
					} catch (IOException e) {
						throw new ClassNotFoundException(name, e);
					}
				} else {
					throw new ClassNotFoundException(name);
				}
			}, acc);
		} catch (java.security.PrivilegedActionException pae) {
			throw (ClassNotFoundException) pae.getException();
		}
	}

	public Class<?> defineClass(String name, Resource res) throws IOException {
		int i = name.lastIndexOf('.');
		URL url = res.getCodeSourceURL();
		if (i != -1) {
			String pkgname = name.substring(0, i);
			Package pkg = getPackage(pkgname);
			Manifest man = res.getManifest();
			if (pkg != null) {
				if (pkg.isSealed()) {
					if (!pkg.isSealed(url)) {
						throw new SecurityException("sealing violation: package " + pkgname + " is sealed");
					}

				} else {
					if ((man != null) && isSealed(pkgname, man)) {
						throw new SecurityException(
								"sealing violation: can't seal package " + pkgname + ": already loaded");
					}
				}
			} else {
				if (man != null) {
					definePackage(pkgname, man, url);
				} else {
					definePackage(pkgname, null, null, null, null, null, null, null);
				}
			}
		}
		java.nio.ByteBuffer bb = res.getByteBuffer();
		if (bb != null) {
			byte[] bs = bb.array();
			bb = ByteBuffer.wrap(bs);
			CodeSigner[] signers = res.getCodeSigners();
			CodeSource cs = new CodeSource(url, signers);
			return defineClass(name, bb, cs);
		} else {
			byte[] b = res.getBytes();
			CodeSigner[] signers = res.getCodeSigners();
			CodeSource cs = new CodeSource(url, signers);
			return defineClass(name, b, 0, b.length, cs);
		}
	}

	private boolean isSealed(String name, Manifest man) {
		String path = name.replace('.', '/').concat("/");
		Attributes attr = man.getAttributes(path);
		String sealed = null;
		if (attr != null) {
			sealed = attr.getValue(Name.SEALED);
		}
		if (sealed == null) {
			if ((attr = man.getMainAttributes()) != null) {
				sealed = attr.getValue(Name.SEALED);
			}
		}
		return "true".equalsIgnoreCase(sealed);
	}

	public Class<?> defineClass(String name, byte[] data) {
		return defineClass(name, data, 0, data.length);
	}

	public void toResolveClass(Class c) {
		resolveClass(c);
	}

}

final class FactoryURLClassLoader extends ClientClassLoader {
	FactoryURLClassLoader(URL[] urls, java.lang.ClassLoader parent) {
		super(urls, parent);
	}

	FactoryURLClassLoader(URL[] urls) {
		super(urls);
	}

	public final Class loadClass(String name, boolean resolve) throws ClassNotFoundException {
		SecurityManager sm = System.getSecurityManager();
		if (sm != null) {
			int i = name.lastIndexOf('.');
			if (i != -1) {
				sm.checkPackageAccess(name.substring(0, i));
			}
		}
		return super.loadClass(name, resolve);
	}
}

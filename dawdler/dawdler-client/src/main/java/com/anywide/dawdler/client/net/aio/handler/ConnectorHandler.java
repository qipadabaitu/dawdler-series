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
package com.anywide.dawdler.client.net.aio.handler;

import java.nio.channels.CompletionHandler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.anywide.dawdler.client.conf.ClientConfigParser;
import com.anywide.dawdler.client.net.aio.session.SocketSession;
import com.anywide.dawdler.core.bean.AuthRequestBean;
import com.anywide.dawdler.core.handler.IoHandler;
import com.anywide.dawdler.core.handler.IoHandlerFactory;
import com.anywide.dawdler.core.net.aio.handler.ReaderHandler;
import com.anywide.dawdler.util.CertificateOperator;

/**
 * @author jackson.song
 * @version V1.0
 * @Title ConnectorHandler.java
 * @Description aio实现连接处理器，初始化session，同时做身份校验
 * @date 2015年3月12日
 * @email suxuan696@gmail.com
 */
public class ConnectorHandler implements CompletionHandler<Void, SocketSession> {
	private static final Logger logger = LoggerFactory.getLogger(ConnectorHandler.class);
	private static final ReaderHandler readerHandler = new ReaderHandler();
	private final IoHandler ioHandler = IoHandlerFactory.getHandler();

	@Override
	public void completed(Void result, SocketSession session) {
		CertificateOperator certificate = new CertificateOperator(
				ClientConfigParser.getClientConfig().getCertificatePath());
		try {
			session.init();
		} catch (Exception e) {
			session.close(false);
			logger.error("", e);
			return;
		}
		if (!session.isClose()) {
			AuthRequestBean auth = new AuthRequestBean();
			try {
				auth.setUser(session.getUser());
				auth.setPassword(certificate.encrypt(session.getPassword().getBytes()));
				auth.setPath(session.getPath());
				session.getDawdlerConnection().writeFirst(session.getPath(), auth, session);
				readerHandler.process(session);
			} catch (Exception e) {
				session.close(false);
				logger.error("", e);
			}
		}
	}

	@Override
	public void failed(Throwable exc, SocketSession socketSession) {
		socketSession.getInitLatch().countDown();
		if (ioHandler != null)
			ioHandler.exceptionCaught(socketSession, exc);
		try {
			socketSession.close();
		} catch (Exception e) {
			logger.error("", e);
		}
		logger.error("", exc);
	}
}

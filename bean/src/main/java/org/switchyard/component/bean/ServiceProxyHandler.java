/* 
 * JBoss, Home of Professional Open Source 
 * Copyright 2011 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @author tags. All rights reserved. 
 * See the copyright.txt in the distribution for a 
 * full listing of individual contributors.
 *
 * This copyrighted material is made available to anyone wishing to use, 
 * modify, copy, or redistribute it subject to the terms and conditions 
 * of the GNU Lesser General Public License, v. 2.1. 
 * This program is distributed in the hope that it will be useful, but WITHOUT A 
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A 
 * PARTICULAR PURPOSE.  See the GNU Lesser General Public License for more details. 
 * You should have received a copy of the GNU Lesser General Public License, 
 * v.2.1 along with this distribution; if not, write to the Free Software 
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, 
 * MA  02110-1301, USA.
 */

package org.switchyard.component.bean;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;

import org.apache.log4j.Logger;
import org.switchyard.Exchange;
import org.switchyard.ExchangePattern;
import org.switchyard.HandlerException;
import org.switchyard.Message;
import org.switchyard.ServiceReference;
import org.switchyard.component.bean.deploy.BeanDeploymentMetaData;
import org.switchyard.component.bean.internal.context.ContextProxy;
import org.switchyard.deploy.ServiceHandler;
import org.switchyard.exception.SwitchYardException;

/**
 * Service/Provider proxy handler.
 * <p/>
 * Handler for converting extracting Service operation invocation details from
 * an ESB {@link Exchange}, making the invocation and then mapping the invocation
 * return/result onto the {@link Message Exchange Message} (if the Exchange pattern
 * is {@link ExchangePattern#IN_OUT IN_OUT}).
 *
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
public class ServiceProxyHandler implements ServiceHandler {

    private static Logger _logger = Logger.getLogger(ServiceProxyHandler.class);

    /**
     * The Service bean instance being proxied to.
     */
    private Object _serviceBean;
    /**
     * The Service bean metadata.
     */
    private BeanServiceMetadata _serviceMetadata;
    /**
     * Deployment metadata.
     */
    private BeanDeploymentMetaData _beanDeploymentMetaData;
    
    private Map<String, ServiceReference> _references = 
            new HashMap<String, ServiceReference>();

    /**
     * Public constructor.
     *
     * @param serviceBean     The Service bean instance being proxied to.
     * @param serviceMetadata The Service bean metadata.
     * @param beanDeploymentMetaData Deployment metadata.
     */
    public ServiceProxyHandler(Object serviceBean,
            BeanServiceMetadata serviceMetadata, 
            BeanDeploymentMetaData beanDeploymentMetaData) {
        _serviceBean = serviceBean;
        _serviceMetadata = serviceMetadata;
        _beanDeploymentMetaData = beanDeploymentMetaData;
    }

    /**
     * Called when a message is sent through an exchange.
     *
     * @param exchange an {@code Exchange} instance containing a message to be processed.
     * @throws HandlerException when handling of the message event fails (e.g. invalid request message).
     */
    public void handleMessage(Exchange exchange) throws HandlerException {
        handle(exchange);
    }

    /**
     * Called when a fault is generated while processing an exchange.
     *
     * @param exchange an {@code Exchange} instance containing a message to be processed.
     */
    public void handleFault(Exchange exchange) {
    }
    
    /**
     * Add the specified reference to the handler.
     * @param reference service reference
     */
    public void addReference(ServiceReference reference) {
        _references.put(reference.getName().getLocalPart(), reference);
    }

    /**
     * Handle the Service bean invocation.
     *
     * @param exchange The Exchange instance.
     * @throws BeanComponentException Error invoking Bean component operation.
     */
    private void handle(Exchange exchange) throws BeanComponentException {
        Invocation invocation = _serviceMetadata.getInvocation(exchange);

        if (invocation != null) {
            try {
                ExchangePattern exchangePattern = exchange.getContract().getServiceOperation().getExchangePattern();

                if (_logger.isDebugEnabled()) {
                    _logger.debug("CDI Bean Service ExchangeHandler proxy class received " + exchangePattern + " Exchange ("
                            + System.identityHashCode(exchange) + ") for Bean Service '"
                            + exchange.getServiceName() + "'.  Invoking bean method '" + invocation.getMethod().getName() + "'.");
                }

                Object responseObject;
                ContextProxy.setContext(exchange.getContext());
                try {
                    responseObject = invocation.getMethod().invoke(_serviceBean, invocation.getArgs());
                } finally {
                    ContextProxy.setContext(null);
                }
                
                if (exchangePattern == ExchangePattern.IN_OUT) {
                    Message message = exchange.createMessage();
                    message.setContent(responseObject);
                    exchange.send(message);
                }
            } catch (IllegalAccessException e) {
                throw new BeanComponentException("Cannot invoke operation '" + invocation.getMethod().getName() + "' on bean component '" + _serviceBean.getClass().getName() + "'.", e);
            } catch (InvocationTargetException e) {
                e.printStackTrace();
                throw new BeanComponentException("Invocation of operation '" + invocation.getMethod().getName() + "' on bean component '" + _serviceBean.getClass().getName() + "' failed with exception.  See attached cause.", e);
            }
        } else {
            throw new SwitchYardException("Unexpected error.  BeanServiceMetadata should return an Invocation instance, or throw a BeanComponentException.");
        }
    }

    @Override
    public void start() {
        // Initialise any client proxies to the started service...
        for (ClientProxyBean proxyBean : _beanDeploymentMetaData.getClientProxies()) {
            if (_references.containsKey(proxyBean.getServiceName())) {
                proxyBean.setService(_references.get(proxyBean.getServiceName()));
            }
        }
    }

    @Override
    public void stop() {
        // NOP
    }
}

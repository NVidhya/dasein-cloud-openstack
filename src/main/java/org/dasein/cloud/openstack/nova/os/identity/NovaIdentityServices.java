/**
 * Copyright (C) 2009-2014 Dell, Inc.
 * See annotations for authorship information
 *
 * ====================================================================
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ====================================================================
 */

package org.dasein.cloud.openstack.nova.os.identity;

import org.dasein.cloud.identity.AbstractIdentityServices;
import org.dasein.cloud.openstack.nova.os.NovaOpenStack;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Core gateway into Dasein Cloud identity services for OpenStack Nova.
 * @author George Reese (george.reese@imaginary.com)
 * @since 2011.10
 * @version 2011.10
 */
public class NovaIdentityServices extends AbstractIdentityServices {
    private NovaOpenStack provider;

    public NovaIdentityServices(@Nonnull NovaOpenStack cloud) {
        provider = cloud;
    }

    @Override
    public @Nullable NovaKeypair getShellKeySupport() {
        if( provider.getProviderName().equals("Rackspace") ) {
            return null;
        }
        return new NovaKeypair(provider);
    }
}

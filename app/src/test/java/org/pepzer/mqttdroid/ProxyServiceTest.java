/* Copyright © 2017 Giuseppe Zerbo. All rights reserved.
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.pepzer.mqttdroid;

import static org.junit.Assert.assertArrayEquals;

import org.junit.Test;

import java.util.ArrayList;
import java.util.regex.Pattern;

public class ProxyServiceTest {

    @Test
    public void testWildcardPatterns() {
        String topicAll = "/mqtt/test/#";
        String topicBeneath1 = "/mqtt/test/+";
        String topicBeneath2 = "/mqtt/test/+/client/+";
        String topicBeneathAll = "/mqtt/test/+/#";

        String[] topicTests = {
                "/mqtt/test",
                "/mqtt/test/foo",
                "/mqtt/test/foo/bar",
                "/mqtt/test/foo/client/bar"
        };

        Pattern patAll = Pattern.compile(ProxyService.subToRegex(topicAll));
        Pattern patBeneath1 = Pattern.compile(ProxyService.subToRegex(topicBeneath1));
        Pattern patBeneath2 = Pattern.compile(ProxyService.subToRegex(topicBeneath2));
        Pattern patBeneathAll = Pattern.compile(ProxyService.subToRegex(topicBeneathAll));

        ArrayList<Boolean> resAll = new ArrayList<Boolean>();
        ArrayList<Boolean> resBeneath1 = new ArrayList<Boolean>();
        ArrayList<Boolean> resBeneath2 = new ArrayList<Boolean>();
        ArrayList<Boolean> resBeneathAll = new ArrayList<Boolean>();

        boolean result;
        for (String topic : topicTests) {
            result = patAll.matcher(topic).matches();
            resAll.add(result);

            result = patBeneath1.matcher(topic).matches();
            resBeneath1.add(result);

            result = patBeneath2.matcher(topic).matches();
            resBeneath2.add(result);

            result = patBeneathAll.matcher(topic).matches();
            resBeneathAll.add(result);
        }

        Boolean[] expectedAll = {true, true, true, true};
        assertArrayEquals("All", resAll.toArray(new Boolean[resAll.size()]), expectedAll);

        Boolean[] expectedBeneath1 = {false, true, false, false};
        assertArrayEquals("Beneath1", resBeneath1.toArray(new Boolean[resBeneath1.size()]), expectedBeneath1);

        Boolean[] expectedBeneath2 = {false, false, false, true};
        assertArrayEquals("Beneath2", resBeneath2.toArray(new Boolean[resBeneath2.size()]), expectedBeneath2);

        Boolean[] expectedBeneathAll = {false, true, true, true};
        assertArrayEquals("BeneathAll", resBeneathAll.toArray(new Boolean[resBeneathAll.size()]), expectedBeneathAll);
    }

}

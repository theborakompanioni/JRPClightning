/**
 * Copyright 2019-2020 Vincenzo Palazzo vincenzo.palazzo@protonmail.com
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package jrpc.clightning;

import jrpc.clightning.commands.Command;
import jrpc.clightning.commands.CommandRPCMediator;
import jrpc.clightning.commands.ICommandKey;
import jrpc.clightning.commands.IRPCCommand;
import jrpc.clightning.exceptions.CLightningException;
import jrpc.clightning.model.*;
import jrpc.clightning.model.types.*;
import jrpc.clightning.service.socket.CLightningSocket;
import jrpc.exceptions.ServiceException;
import jrpc.service.CLightningLogger;
import java.util.HashMap;

/**
 * @author https://github.com/vincenzopalazzo
 */
public class CLightningRPC {

    private static final Class TAG = CLightningRPC.class;
    private static CLightningRPC SINGLETON;

    protected static final String JOIN_TOKEN_PROP = "+";

    public static CLightningRPC getInstance() {
        if (SINGLETON == null) {
            SINGLETON = new CLightningRPC();
        }
        return SINGLETON;
    }

    protected CLightningSocket socket;
    protected CommandRPCMediator mediatorCommand;

    private CLightningRPC() {
        try {
            socket = new CLightningSocket();
            this.mediatorCommand = new CommandRPCMediator(socket);
        } catch (CLightningException clex) {
            CLightningLogger.getInstance().error(TAG, "CLightningRPC throws an exception " + clex.getLocalizedMessage());
            clex.printStackTrace();
        } catch (ServiceException e) {
            socket = null;
            throw new CLightningException("Configuration socket error, Message error is:" + e.getLocalizedMessage());
        }
    }

    public CLightningFeeRate feeRate(FeeRateStyle style){
        if(style == null){
            throw new CLightningException("style null");
        }
        HashMap<String, Object> payload = new HashMap<>();
        payload.put("style", style.toString());
        return (CLightningFeeRate) mediatorCommand.runCommand(Command.FEERATES, payload);
    }

    public CLightningGetRoutes getRoute(String id, String mSatoshi, float riskFactor){
        return getRoute(id, mSatoshi, riskFactor, 9, "", "", 20);
    }

    public CLightningGetRoutes getRoute(String id, String mSatoshi, float riskFactor, int cltv,
                           String fromid, String fuzzpercent, int maxHope, ExcludeChannel... exclude){
        doCheckString("getRoute", "id", id, false);
        doCheckString("getRoute", "mSatoshi", mSatoshi, false);
        doCheckString("getRoute", "fromid", fromid, true);
        doCheckString("getRoute", "fuzzpercent", fuzzpercent, true);
        doCheckPositiveNumber("getRoute", "riskFactor", riskFactor);
        doCheckPositiveNumber("getRoute", "cltv", cltv);
        doCheckPositiveNumber("getRoute", "maxHope", maxHope);

        HashMap<String, Object> payload = new HashMap<>();

        payload.put("id", id);
        payload.put("msatoshi", maxHope);
        payload.put("riskfactor", riskFactor);
        payload.put("cltv", cltv);
        payload.put("maxhope", maxHope);

        if(!fromid.isEmpty()){
            payload.put("fromid", fromid);
        }

        if(!fuzzpercent.isEmpty()){
            payload.put("fuzzpercent", fuzzpercent);
        }

        if(exclude.length > 0){
            payload.put("exclude", exclude);
        }
        return (CLightningGetRoutes) mediatorCommand.runCommand(Command.GETROUTE, payload);
    }

    public CLightningGetInfo getInfo() {
        CLightningGetInfo resultCommand = (CLightningGetInfo) mediatorCommand.runCommand(Command.GETINFO, "");
        return resultCommand;
    }

    public String getNewAddress(AddressType type) {
        if (type == null) {
            throw new IllegalArgumentException("Type address is null");
        }
        String typeString;
        if (type.equals(AddressType.BECH32)) {
            typeString = "bech32";
        } else {
            typeString = "p2sh-segwit";
        }
        String payload = "addresstype=" + typeString;
        CLightningLogger.getInstance().debug(TAG, "Payload: " + payload);
        CLightningNewAddress resultCommand = (CLightningNewAddress) mediatorCommand.runCommand(Command.NEWADDR, payload);
        if (type.equals(AddressType.BECH32)) {
            return resultCommand.getBech32();
        }
        return resultCommand.getP2shSegwit();
    }

    public CLightningInvoice getInvoice(int mSatoshi, String label, String description) {
        return this.getInvoice(mSatoshi, label, description, "", new String[]{}, "", false);
    }

    public CLightningInvoice getInvoice(int mSatoshi, String label, String description, String expiry) {
        return this.getInvoice(mSatoshi, label, description, expiry, new String[]{}, "", false);
    }

    public CLightningInvoice getInvoice(int mSatoshi, String label, String description, String expiry, String[] fallbacks, String preImage, boolean exposePrivateChannels) {
        if (label == null || label.isEmpty()) {
            throw new CLightningException("The method getInvoice have the parameter label null");
        }
        if (description == null || description.trim().isEmpty()) {
            throw new CLightningException("The method getInvoice have the parameter description null");
        }
        if (expiry == null) {
            throw new CLightningException("The method getInvoice have the parameter expiry null");
        }
        if (fallbacks == null) {
            throw new CLightningException("The method getInvoice have the parameter fallbacks null");
        }
        if (preImage == null) {
            throw new CLightningException("The method getInvoice have the parameter preImage null");
        }
        StringBuilder optionalString = new StringBuilder();
        if (!expiry.trim().isEmpty()) {
            optionalString.append(JOIN_TOKEN_PROP).append("expiry=").append(expiry.trim());
        }

        if (fallbacks.length > 0) {
            optionalString.append(parsingArrayElement("fallbacks", fallbacks));
        }

        if (!preImage.trim().isEmpty()) {
            optionalString.append(JOIN_TOKEN_PROP).append("preimage=").append(preImage.trim());
        }
        optionalString.append(JOIN_TOKEN_PROP).append("exposeprivatechannels=").append(exposePrivateChannels);

        StringBuilder payload = new StringBuilder();

        payload.append("msatoshi=").append(mSatoshi);
        payload.append(JOIN_TOKEN_PROP).append("label=").append(label);
        payload.append(JOIN_TOKEN_PROP).append("description=").append(description.trim());
        if (!optionalString.toString().trim().isEmpty()) {
            payload.append(optionalString.toString());
        }

        String payloadResult = payload.toString();
        CLightningLogger.getInstance().debug(TAG, "Payload: " + payloadResult);

        return (CLightningInvoice) mediatorCommand.runCommand(Command.INVOICE, payloadResult);
    }

    public CLightningListInvoices getListInvoices(String label) {
        if (label == null) {
            throw new CLightningException("The method getListInvoices have the parameter label null");
        }
        String payload = "";
        if (!label.isEmpty()) {
            payload = "label=" + label;
        }

        return (CLightningListInvoices) mediatorCommand.runCommand(Command.LISTINVOICE, payload);
    }

    public CLightningListInvoices getListInvoices() {
        return getListInvoices("");
    }

    public CLightningInvoice delInvoice(String label, String status) {
        if (label == null) {
            throw new CLightningException("The method getListInvoices have the parameter label null");
        }
        if (status == null || status.isEmpty()) {
            throw new CLightningException("The method getListInvoices have the parameter label not valid (empty or null)");
        }

        StringBuilder payload = new StringBuilder();
        payload.append(JOIN_TOKEN_PROP).append("label=").append(label);
        payload.append(JOIN_TOKEN_PROP).append("status=").append(status);
        String payloadString = payload.toString();
        CLightningLogger.getInstance().debug(TAG, "Payload for command delInvoice: " + payloadString);

        return (CLightningInvoice) mediatorCommand.runCommand(Command.DELINVOICE, payloadString);
    }

    public String autoCleanInvoice() {
        return autoCleanInvoice("", "");
    }

    public String autoCleanInvoice(String cycleSeconds, String expiredBy) {
        if (cycleSeconds == null) {
            throw new CLightningException("The method getListInvoices have the parameter cycleSeconds null");
        }
        if (expiredBy == null) {
            throw new CLightningException("The method getListInvoices have the parameter expiredBy null");
        }
        StringBuilder payload = new StringBuilder();
        if (!cycleSeconds.trim().isEmpty()) {
            payload.append("cycle_seconds=").append(cycleSeconds.trim());
        }
        if (!expiredBy.trim().isEmpty()) {
            payload.append(JOIN_TOKEN_PROP).append("expired_by=").append(expiredBy.trim());
        }

        String payloadString = payload.toString();
        CLightningLogger.getInstance().debug(TAG, "Payload: " + payloadString);

        return (String) mediatorCommand.runCommand(Command.AUTOCLEANINVOICE, payloadString);
    }

    public CLightningBitcoinTx txPrepare(String feerate, String minconf, BitcoinOutput... bitcoinOutputs) {
        if (bitcoinOutputs.length == 0) {
            throw new CLightningException("The method getListInvoices have the parameter output is/are empty");
        }
        if (feerate == null) {
            throw new CLightningException("The method getListInvoices have the parameter feerate is null");
        }
        if (minconf == null) {
            throw new CLightningException("The method getListInvoices have the parameter minconf is null");
        }

        for (BitcoinOutput output : bitcoinOutputs){
            if(output.getAmount().isEmpty()){
                output.setAmount("all");
            }
        }

        HashMap<String, Object> payload = new HashMap<>();
        payload.put("outputs", bitcoinOutputs);
        payload.put("feerate", minconf);
        payload.put("minconf", feerate);

        CLightningLogger.getInstance().debug(TAG, "Payload command txPrepare: " + payload.toString());
        return (CLightningBitcoinTx) mediatorCommand.runCommand(Command.TXPREPARE, payload);
    }

    public CLightningBitcoinTx txPrepare(BitcoinOutput... bitcoinOutputs) {
        return this.txPrepare("", "", bitcoinOutputs);
    }

    public CLightningBitcoinTx txDiscard(String txId) {
        if (txId == null || txId.trim().isEmpty()) {
            throw new CLightningException("The method txDiscard have the parameter txId is empty or null");
        }

        String pyload = "txid=" + txId;
        return (CLightningBitcoinTx) mediatorCommand.runCommand(Command.TXDISCARD, pyload);
    }

    public CLightningBitcoinTx withDraw(String destination, String satoshi, String feerate, String minconf) {
        if (destination == null || destination.trim().isEmpty()) {
            throw new CLightningException("The method getListInvoices have the parameter destination is/are empty");
        }
        if (satoshi == null) {
            throw new CLightningException("The method getListInvoices have the parameter satoshi is/are empty");
        }
        if (feerate == null) {
            throw new CLightningException("The method getListInvoices have the parameter feerate is null");
        }
        if (minconf == null) {
            throw new CLightningException("The method getListInvoices have the parameter minconf is null");
        }

        StringBuilder payload = new StringBuilder();

        payload.append("destination=").append(destination.trim());

        if (satoshi.trim().isEmpty()) {
            payload.append(JOIN_TOKEN_PROP).append("satoshi=").append("all");
        } else {
            payload.append(JOIN_TOKEN_PROP).append("satoshi=").append(satoshi.trim());
        }
        if (!feerate.isEmpty()) {
            payload.append(JOIN_TOKEN_PROP).append("feerate=").append(feerate.trim());
        }
        if (!minconf.trim().isEmpty()) {
            payload.append(JOIN_TOKEN_PROP).append("minconf=").append(minconf.trim());
        }

        String payloadString = payload.toString();
        CLightningLogger.getInstance().debug(TAG, "Payload command withDraw: " + payloadString);
        return (CLightningBitcoinTx) mediatorCommand.runCommand(Command.WITHDRAW, payloadString);
    }

    public CLightningBitcoinTx withDraw(String destination, String satoshi) {
        return this.withDraw(destination, satoshi, "", "");
    }

    public CLightningBitcoinTx close(String id, String unilateraltimeout) {
        if (id == null || id.trim().isEmpty()) {
            throw new CLightningException("The method close have the parameter id is null");
        }
        if (unilateraltimeout == null) {
            throw new CLightningException("The method close have the parameter unilateraltimeout is null");
        }

        StringBuilder payload = new StringBuilder();
        payload.append("id=").append(id);

        if (!unilateraltimeout.trim().isEmpty()) {
            payload.append(JOIN_TOKEN_PROP).append("unilateraltimeout=").append(unilateraltimeout.trim());
        }

        String payloadString = payload.toString();
        CLightningLogger.getInstance().debug(TAG, "Payload command close: " + payloadString);
        return (CLightningBitcoinTx) mediatorCommand.runCommand(Command.CLOSE, payloadString);
    }

    public CLightningBitcoinTx close(String id) {
        return this.close(id, "");
    }

    public CLightningBitcoinTx fundChannel(String id, String satoshi) {
        return fundChannel(id, satoshi, "normal", true, 1, new String[]{});
    }

    /**
     * from version 0.7.2.1 the parameter satoshi were change from satoshi to amount, it mean that the library
     * support the parameter amount but you can support the satoshi parameter with a personal RPC method.
     * Look here TODO
     * @param id
     * @param satoshi
     * @param feerate
     * @param announce
     * @param minConf
     * @param utxos
     * @return CLightningBitcoinTx
     */
    public CLightningBitcoinTx fundChannel(String id, String satoshi, String feerate, boolean announce, int minConf, String[] utxos) {
        doCheckString("fundChannel", "id", id, false);
        doCheckString("fundChannel", "satoshi", satoshi, false);
        doCheckString("fundChannel", "feerate", feerate, false);
        if (minConf <= 0) {
            throw new CLightningException("The method fundChannel have the parameter minconf is minus than 1");
        }
        StringBuilder payload = new StringBuilder();
        payload.append("id=").append(id.trim());
        payload.append(JOIN_TOKEN_PROP).append("amount=").append(satoshi.trim());
        payload.append(JOIN_TOKEN_PROP).append("feerate=").append(feerate.trim());
        if (!announce) {
            payload.append(JOIN_TOKEN_PROP).append("announce=").append(announce);
        }

        if (utxos.length > 0) {
            payload.append(parsingArrayElement("utxos", utxos));
        }

        String payloadString = payload.toString();
        CLightningLogger.getInstance().debug(TAG, "Payload method fundChannel is: " + payloadString);
        return (CLightningBitcoinTx) mediatorCommand.runCommand(Command.FUNDCHANNEL, payloadString);
    }

    public CLightningListFounds listFunds() {
        String payloadString = "";
        return (CLightningListFounds) mediatorCommand.runCommand(Command.LISTFOUNDS, payloadString);
    }

    public String connect(String id, String host, String port) {
        if (id == null || id.trim().isEmpty()) {
            throw new CLightningException("The method connect have the parameter id is null or empty");
        }
        if (host == null) {
            throw new CLightningException("The method connect have the parameter host is null");
        }
        if (port == null) {
            throw new CLightningException("The method connect have the parameter port is null");
        }

        StringBuilder payload = new StringBuilder();
        payload.append("id=").append(id).append("@");
        if (host.trim().isEmpty()) {
            payload.append("127.0.0.1");
        } else {
            payload.append(host);
        }
        payload.append(":");
        if (port.trim().isEmpty()) {
            payload.append("9735");
        } else {
            payload.append(port);
        }

        String payloadString = payload.toString();
        CLightningLogger.getInstance().debug(TAG, "Payload for command connect is: " + payloadString);
        CLightningChannelId channelId = (CLightningChannelId) mediatorCommand.runCommand(Command.CONNECT, payloadString);
        return channelId.getId();
    }

    public String connect(String id) {
        if (id == null || id.trim().isEmpty()) {
            throw new CLightningException("The method connect have the parameter id is null or empty");
        }
        return this.connect(id, "", "");
    }

    public boolean disconnect(String id){
        return this.disconnect(id, true);
    }

    public boolean disconnect(String id, boolean force){
       doCheckString("disconnect", "id", id, false);

       HashMap<String, Object> payload = new HashMap<>();

       payload.put("id", id);
       payload.put("force", force);

       mediatorCommand.runCommand(Command.DISCONNECT, payload);
       //If I arrive here no error happened and it mean that there isn't error
       return true;
    }

    public CLightningPay pay(String bolt11) {
        return pay(bolt11, "", "", 10, "", 60, "");
    }

    public CLightningPay pay(String bolt11, String satoshi, String label, float riskFactor, String maxFeePercent, int retryFor, String maxDelay) {
        if (bolt11 == null || bolt11.trim().isEmpty()) {
            throw new CLightningException("The method pay have the parameter bolt11 is null or empty");
        }
        if (satoshi == null) {
            throw new CLightningException("The method pay have the parameter satoshi is null");
        }
        if (label == null) {
            throw new CLightningException("The method pay have the parameter label is null");
        }
        if (maxFeePercent == null) {
            throw new CLightningException("The method pay have the parameter maxFeePercent is null");
        }
        if (maxDelay == null) {
            throw new CLightningException("The method pay have the parameter maxDelay is null");
        }

        StringBuilder payload = new StringBuilder();
        payload.append("bolt11=").append(bolt11);
        if (!satoshi.trim().isEmpty()) {
            payload.append(JOIN_TOKEN_PROP).append("satoshi=").append(satoshi);
        }

        if (!label.trim().isEmpty()) {
            payload.append(JOIN_TOKEN_PROP).append("label=").append(label);
        }

        payload.append(JOIN_TOKEN_PROP).append("riskfactor=").append(riskFactor);

        if (!maxFeePercent.trim().isEmpty()) {
            payload.append(JOIN_TOKEN_PROP).append("maxfeepercent=").append(maxFeePercent);
        }

        payload.append(JOIN_TOKEN_PROP).append("retry_for=").append(retryFor);

        if (!maxDelay.trim().isEmpty()) {
            payload.append(JOIN_TOKEN_PROP).append("maxDelay=").append(maxDelay);
        }

        String payloadString = payload.toString();
        CLightningLogger.getInstance().debug(TAG, "Payload for pay connect is: " + payloadString);
        CLightningPay pay = (CLightningPay) mediatorCommand.runCommand(Command.PAY, payloadString);
        return pay;
    }

    public CLightningListSendPays listSendPays() {
        return this.listSendPays("", "");
    }

    public CLightningListSendPays listSendPays(String bolt11, String paymentHash) {
        if (bolt11 == null || paymentHash == null) {
            throw new CLightningException("The method pay have the parameter bolt11 or/and paymentHash is/are null");
        }

        StringBuilder payload = new StringBuilder();
        if (!bolt11.trim().isEmpty()) {
            payload.append("bolt11=").append(bolt11.trim());
            if (!paymentHash.trim().isEmpty()) {
                payload.append(JOIN_TOKEN_PROP).append("payment_hash=").append(paymentHash.trim());
            }
        } else {
            if (!paymentHash.trim().isEmpty()) {
                payload.append("payment_hash=").append(paymentHash.trim());
            }
        }

        String payloadString = payload.toString();
        CLightningLogger.getInstance().debug(TAG, "Payload for command listSendPays is: " + payloadString);
        CLightningListSendPays list = (CLightningListSendPays) mediatorCommand.runCommand(Command.LISTSENDPAYS, payloadString);
        return list;
    }

    public CLightningListChannels listChannels() {
        return this.listChannels("", "");
    }

    public CLightningListChannels listChannels(String shortIdChannel, String source) {
        if (shortIdChannel == null) {
            throw new IllegalArgumentException("The shortIdChannel inside the method listChannels is null");
        }
        if (source == null) {
            throw new IllegalArgumentException("The source inside the method listChannels is null");
        }
        StringBuilder payload = new StringBuilder();
        if (!shortIdChannel.trim().isEmpty()) {
            payload.append("short_channel_id=").append(shortIdChannel.trim());
            if (!source.trim().isEmpty()) {
                payload.append(JOIN_TOKEN_PROP).append("source=").append(source.trim());
            }
        } else if (!source.isEmpty()) {
            payload.append("source=").append(source.trim());
        }

        String payloadString = payload.toString();
        CLightningLogger.getInstance().debug(TAG, "Payload for command listChannels " + payloadString);
        CLightningListChannels channelsList = (CLightningListChannels) mediatorCommand.runCommand(Command.LISTCHANNELS, payloadString);
        return channelsList;
    }

    public CLightningListPeers listPeers() {
        return this.listPeers("", "");
    }

    public CLightningListPeers listPeers(String id, String level) {
        if (id == null) {
            throw new IllegalArgumentException("The id inside the method listChannels is null");
        }
        if (level == null) {
            throw new IllegalArgumentException("The level inside the method listChannels is null");
        }
        StringBuilder payload = new StringBuilder();
        if (!id.trim().isEmpty()) {
            payload.append("id=").append(id.trim());
            if (!level.trim().isEmpty()) {
                payload.append(JOIN_TOKEN_PROP).append("level=").append(level.trim());
            }
        } else if (!level.isEmpty()) {
            payload.append("level=").append(level.trim());
        }

        String payloadString = payload.toString();
        CLightningLogger.getInstance().debug(TAG, "Payload for command listPeers " + payloadString);
        CLightningListPeers listPeers = (CLightningListPeers) mediatorCommand.runCommand(Command.LISTPEERS, payloadString);
        return listPeers;
    }

    public CLightningDecodePay decodePay(String bolt11) {
        return this.decodePay(bolt11, "");
    }

    public CLightningDecodePay decodePay(String bolt11, String description) {
        if (bolt11 == null || bolt11.trim().isEmpty()) {
            throw new IllegalArgumentException("The bolt11 inside the method decodePay is null or empty");
        }
        if (description == null) {
            throw new IllegalArgumentException("The level inside the method decodePay is null");
        }

        StringBuilder payload = new StringBuilder();
        payload.append("bolt11=").append(bolt11.trim()).append(JOIN_TOKEN_PROP);

        if (!description.trim().isEmpty()) {
            payload.append("description=").append(description.trim());
        }

        String payloadString = payload.toString();
        CLightningLogger.getInstance().debug(TAG, "The payload for method decodePay is: " + payloadString);
        CLightningDecodePay decodePay = (CLightningDecodePay) mediatorCommand.runCommand(Command.DECODEPAY, payloadString);
        return decodePay;
    }

    public void registerCommand(ICommandKey key, IRPCCommand command){
        if(key == null || command == null){
            throw new IllegalArgumentException("Key and/or command null");
        }
        mediatorCommand.registerCommand(key, command);
    }

    public <T> T runRegisterCommand(ICommandKey key, HashMap<String, Object> payload){
        return mediatorCommand.runRegisterCommand(key, payload);
    }

    //Utility methods
    private void doCheckString(String command, String name, String value, boolean onlyNull){
        if(value == null || value.isEmpty()){
            String message = "Propriety " + name + " in the command " + command;
            if(name == null){
                message += " null";
            }else if(onlyNull){
                return; //Good for the library
            }else {
                message += " empty";
            }
            throw new CLightningException(message);
        }
    }

    private void doCheckPositiveNumber(String command, String name, Number value){
        if(value.floatValue() < 0){
            String message = "Propriety " + name + " in the command " + command + " must be positive";
            throw new CLightningException(message);
        }
    }

    @Deprecated
    protected String parsingBitcoinOutputs(BitcoinOutput... bitcoinOutputs) {
        if (bitcoinOutputs.length == 0) {
            throw new CLightningException("The method getListInvoices have the parameter output is/are empty");
        }

        StringBuilder outputBuilder = new StringBuilder();
        for (int i = 0; i < bitcoinOutputs.length; i++) {
            if (i == 0) {
                outputBuilder.append(bitcoinOutputs[i]);
                continue;
            }
            outputBuilder.append("&").append(bitcoinOutputs[i]);
        }

        String outputsString = outputBuilder.toString();
        CLightningLogger.getInstance().debug(TAG, "Output converted into string is: " + outputsString);
        return outputsString;
    }

    @Deprecated
    protected String parsingArrayElement(String proprietyName, String[] array) {
        if (proprietyName == null || proprietyName.trim().isEmpty()) {
            throw new CLightningException("The internal method \"parsingArrayElement\" have the parameter proprietyName is empty or null");
        }

        if (array == null || array.length <= 0) {
            throw new CLightningException("The internal method \"parsingArrayElement\" have the array null or empty");
        }

        StringBuilder arrayConverted = new StringBuilder();
        arrayConverted.append(JOIN_TOKEN_PROP).append(proprietyName.trim()).append("=[");
        for (int i = 0; i < array.length; i++) {
            if (i == 0) {
                arrayConverted.append(array[i].trim());
                continue;
            }
            arrayConverted.append(",").append(array[i].trim());
        }
        arrayConverted.append("]");
        String result = arrayConverted.toString();
        CLightningLogger.getInstance().debug(TAG, "The result of the method \"arrayConverted\"\n" + result);
        return result;
    }
}

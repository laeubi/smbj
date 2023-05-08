/*
 * Copyright (C)2016 - SMBJ Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hierynomus.ntlm.messages;

import com.hierynomus.ntlm.functions.NtlmFunctions;
import com.hierynomus.protocol.commons.ByteArrayUtils;
import com.hierynomus.protocol.commons.Charsets;
import com.hierynomus.protocol.commons.EnumWithValue;
import com.hierynomus.protocol.commons.buffer.Buffer;
import com.hierynomus.protocol.commons.buffer.Endian;

import static com.hierynomus.ntlm.messages.Utils.*;

import java.util.Set;

/**
 * [MS-NLMP].pdf 2.2.1.3 AUTHENTICATE_MESSAGE
 */
public class NtlmAuthenticate extends NtlmMessage {

    private byte[] lmResponse;
    private byte[] ntResponse;
    private byte[] userName;
    private byte[] domainName;
    private byte[] workstation;
    private byte[] encryptedRandomSessionKey;
    private byte[] mic;
    private boolean integrityEnabled;
    private boolean omitVersion;

    public NtlmAuthenticate(
        byte[] lmResponse, byte[] ntResponse,
        String userName, String domainName, String workstation,
        byte[] encryptedRandomSessionKey, Set<NtlmNegotiateFlag> negotiateFlags,
        WindowsVersion version, boolean isIntegrityEnabled, boolean isOmitVersion) {
        super(negotiateFlags, version);
        this.lmResponse = ensureNotNull(lmResponse);
        this.ntResponse = ensureNotNull(ntResponse);
        this.userName = ensureNotNull(userName);
        this.domainName = ensureNotNull(domainName);
        this.workstation = ensureNotNull(workstation);
        this.encryptedRandomSessionKey = ensureNotNull(encryptedRandomSessionKey);
        this.negotiateFlags = negotiateFlags;
        this.integrityEnabled = isIntegrityEnabled;
        this.omitVersion = isOmitVersion;
    }

    @Override
    public void write(Buffer.PlainBuffer buffer) {
        int baseMessageSize = 64;
        if (integrityEnabled) {
            baseMessageSize += 16;
        }

        if (!omitVersion) {
            baseMessageSize += 8;
        }

        writeNtlmAuthenticate(buffer, baseMessageSize);

        // MIC (16 bytes)
        if (mic != null) {
            buffer.putRawBytes(mic);
        } else if (integrityEnabled) {
            buffer.putUInt64(0L);
            buffer.putUInt64(0L);
        } else {
            // Skipping MIC, not enabled.
        }

        // Payload
        buffer.putRawBytes(lmResponse);
        buffer.putRawBytes(ntResponse);
        buffer.putRawBytes(domainName);
        buffer.putRawBytes(userName);
        buffer.putRawBytes(workstation);
        buffer.putRawBytes(encryptedRandomSessionKey);
    }

    public void setMic(byte[] mic) {
        this.mic = mic;
    }

    public void writeNtlmAuthenticate(Buffer.PlainBuffer buffer, int baseMessageSize) {
        buffer.putString("NTLMSSP\0", Charsets.UTF_8); // Signature (8 bytes)
        buffer.putUInt32(0x03); // MessageType (4 bytes)

        int offset = baseMessageSize; // for the offset
        offset = writeOffsettedByteArrayFields(buffer, lmResponse, offset); // LmChallengeResponseFields (8 bytes)
        offset = writeOffsettedByteArrayFields(buffer, ntResponse, offset); // NtChallengeResponseFields (8 bytes)
        offset = writeOffsettedByteArrayFields(buffer, domainName, offset); // DomainNameFields (8 bytes)
        offset = writeOffsettedByteArrayFields(buffer, userName, offset); // UserNameFields (8 bytes)
        offset = writeOffsettedByteArrayFields(buffer, workstation, offset); // WorkstationFields (8 bytes)
        if (negotiateFlags.contains(NtlmNegotiateFlag.NTLMSSP_NEGOTIATE_KEY_EXCH)) { // TODO probably unnecessary check
            offset = writeOffsettedByteArrayFields(buffer, encryptedRandomSessionKey, offset);
        } else {
            offset = writeOffsettedByteArrayFields(buffer, EMPTY, offset);
        }

        buffer.putUInt32(EnumWithValue.EnumUtils.toLong(negotiateFlags)); // NegotiateFlags (4 bytes)

        // If `omitVersion`, we skip rendering the Version, as some servers don't like it.
        if (negotiateFlags.contains(NtlmNegotiateFlag.NTLMSSP_NEGOTIATE_VERSION)) {
            buffer.putRawBytes(getVersion()); // Version (8 bytes)
        } else if (!omitVersion) {
            buffer.putUInt64(0L);
        }
    }

    /**
     * MS-NLMP 2.2.2.10 VERSION
     *
     * @return
     */
    public byte[] getVersion() {
        Buffer.PlainBuffer plainBuffer = new Buffer.PlainBuffer(Endian.LE);
        plainBuffer.putByte((byte) 0x06); // Major Version 6
        plainBuffer.putByte((byte) 0x01); // Minor Version 1
        plainBuffer.putUInt16(7600); // Product Build 7600
        byte[] reserved = {(byte) 0x00, (byte) 0x00, (byte) 0x00};
        plainBuffer.putRawBytes(reserved); // Reserver 3 bytes
        plainBuffer.putByte((byte) 0x0F); // NTLM Revision Current
        return plainBuffer.getCompactData();
    }

    @Override
    public String toString() {
        return "NtlmAuthenticate{\n" +
            "  mic=" + (mic != null ? ByteArrayUtils.printHex(mic) : "[]") + ",\n" +
            "  lmResponse=" + ByteArrayUtils.printHex(lmResponse) + ",\n" +
            "  ntResponse=" + ByteArrayUtils.printHex(ntResponse) + ",\n" +
            "  domainName='" + NtlmFunctions.unicode(domainName) + "',\n" +
            "  userName='" + NtlmFunctions.unicode(userName) + "',\n" +
            "  workstation='" + NtlmFunctions.unicode(workstation) + "',\n" +
            "  encryptedRandomSessionKey=[<secret>],\n" +
            '}';
    }
}

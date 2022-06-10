package com.strategyobject.substrateclient.rpc.api.section;

import com.strategyobject.substrateclient.common.types.Size;
import com.strategyobject.substrateclient.common.utils.HexConverter;
import com.strategyobject.substrateclient.crypto.*;
import com.strategyobject.substrateclient.rpc.RpcGeneratedSectionFactory;
import com.strategyobject.substrateclient.rpc.api.*;
import com.strategyobject.substrateclient.tests.containers.SubstrateVersion;
import com.strategyobject.substrateclient.tests.containers.TestSubstrateContainer;
import com.strategyobject.substrateclient.transport.ws.WsProvider;
import lombok.val;
import lombok.var;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.Network;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigInteger;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Testcontainers
class AuthorTests {
    private static final int WAIT_TIMEOUT = 10;
    private static final Network network = Network.newNetwork();
    private static final AtomicInteger NONCE = new AtomicInteger(0);

    @Container
    static final TestSubstrateContainer substrate = new TestSubstrateContainer(SubstrateVersion.V3_0_0)
            .withNetwork(network);

    @Test
    void hasKey() throws Exception {
        try (val wsProvider = connect()) {
            val author = RpcGeneratedSectionFactory.create(Author.class, wsProvider);

            val publicKey = PublicKey.fromBytes(
                    HexConverter.toBytes("0xd43593c715fdd31c61141abd04a99fd6822c8558854ccde39a5684e7a56da27d"));
            val keyType = "aura";
            var result = author.hasKey(publicKey, keyType).get(WAIT_TIMEOUT, TimeUnit.SECONDS);

            Assertions.assertFalse(result);

            author.insertKey(keyType, "alice", publicKey).get(WAIT_TIMEOUT, TimeUnit.SECONDS);
            result = author.hasKey(publicKey, keyType).get(WAIT_TIMEOUT, TimeUnit.SECONDS);

            Assertions.assertTrue(result);
        }
    }

    @Test
    void insertKey() throws Exception {
        try (val wsProvider = connect()) {
            val author = RpcGeneratedSectionFactory.create(Author.class, wsProvider);

            Assertions.assertDoesNotThrow(() -> author.insertKey("aura",
                            "alice",
                            PublicKey.fromBytes(
                                    HexConverter.toBytes("0xd43593c715fdd31c61141abd04a99fd6822c8558854ccde39a5684e7a56da27d")))
                    .get(WAIT_TIMEOUT, TimeUnit.SECONDS));
        }
    }

    @Test
    void submitExtrinsic() throws Exception {
        try (val wsProvider = connect()) {
            val chain = RpcGeneratedSectionFactory.create(Chain.class, wsProvider);
            val genesis = chain.getBlockHash(0).get(WAIT_TIMEOUT, TimeUnit.SECONDS);

            val author = RpcGeneratedSectionFactory.create(Author.class, wsProvider);
            Assertions.assertDoesNotThrow(() -> author.submitExtrinsic(createBalanceTransferExtrinsic(genesis, NONCE.getAndIncrement()))
                    .get(WAIT_TIMEOUT, TimeUnit.SECONDS));
        }
    }

    @Test
    void submitAndWatchExtrinsic() throws Exception {
        try (val wsProvider = connect()) {
            val chain = RpcGeneratedSectionFactory.create(Chain.class, wsProvider);
            val genesis = chain.getBlockHash(0).get(WAIT_TIMEOUT, TimeUnit.SECONDS);

            val author = RpcGeneratedSectionFactory.create(Author.class, wsProvider);
            val updateCount = new AtomicInteger(0);
            val status = new AtomicReference<ExtrinsicStatus>();
            val unsubscribe = author.submitAndWatchExtrinsic(
                            createBalanceTransferExtrinsic(genesis, NONCE.getAndIncrement()),
                            (exception, extrinsicStatus) -> {
                                updateCount.incrementAndGet();
                                status.set(extrinsicStatus);
                            })
                    .get(WAIT_TIMEOUT, TimeUnit.SECONDS);

            await()
                    .atMost(WAIT_TIMEOUT * 2, TimeUnit.SECONDS)
                    .untilAtomic(updateCount, greaterThan(0));

            assertNotNull(status.get());

            val result = unsubscribe.get().get(WAIT_TIMEOUT, TimeUnit.SECONDS);

            Assertions.assertTrue(result);
        }
    }

    private WsProvider connect() throws ExecutionException, InterruptedException, TimeoutException {
        val wsProvider = WsProvider.builder()
                .setEndpoint(substrate.getWsAddress())
                .disableAutoConnect()
                .build();

        wsProvider.connect().get(WAIT_TIMEOUT, TimeUnit.SECONDS);
        return wsProvider;
    }

    private Extrinsic<?, ?, ?, ?> createBalanceTransferExtrinsic(BlockHash genesis, int nonce) {
        val specVersion = 264;
        val txVersion = 2;
        val moduleIndex = (byte) 6;
        val callIndex = (byte) 0;
        val tip = 0;
        val call = new BalanceTransfer(moduleIndex, callIndex, AddressId.fromBytes(bobKeyPair().asPublicKey().getData()), BigInteger.valueOf(10));

        val extra = new SignedExtra<>(specVersion, txVersion, genesis, genesis, new ImmortalEra(), BigInteger.valueOf(nonce), BigInteger.valueOf(tip));
        val signedPayload = new SignedPayload<>(call, extra);
        val keyRing = KeyRing.fromKeyPair(aliceKeyPair());

        val signature = sign(keyRing, signedPayload);

        return Extrinsic.createSigned(
                new SignaturePayload<>(
                        AddressId.fromBytes(aliceKeyPair().asPublicKey().getData()),
                        signature,
                        extra
                ), call);
    }

    private Signature sign(KeyRing keyRing, Signable payload) {
        var signed = payload.getBytes();
        val signature = signed.length > 256 ? Hasher.blake2(Size.of256, signed) : signed;

        return Sr25519Signature.from(keyRing.sign(() -> signature));
    }

    private KeyPair aliceKeyPair() {
        val str = "0x98319d4ff8a9508c4bb0cf0b5a78d760a0b2082c02775e6e82370816fedfff48925a225d97aa00682d6a59b95b18780c10d" +
                "7032336e88f3442b42361f4a66011d43593c715fdd31c61141abd04a99fd6822c8558854ccde39a5684e7a56da27d";

        return KeyPair.fromBytes(HexConverter.toBytes(str));
    }

    private KeyPair bobKeyPair() {
        val str = "0x081ff694633e255136bdb456c20a5fc8fed21f8b964c11bb17ff534ce80ebd5941ae88f85d0c1bfc37be41c904e1dfc01de" +
                "8c8067b0d6d5df25dd1ac0894a3258eaf04151687736326c9fea17e25fc5287613693c912909cb226aa4794f26a48";

        return KeyPair.fromBytes(HexConverter.toBytes(str));
    }
}
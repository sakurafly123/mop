package io.streamnative.pulsar.handlers.mqtt; /**
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

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.mqtt.MqttDecoder;
import io.netty.handler.codec.mqtt.MqttEncoder;
import io.netty.handler.ssl.ApplicationProtocolConfig;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.SslProvider;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.BinaryCodec;
import org.conscrypt.OpenSSLProvider;
import org.conscrypt.PSKKeyManager;
import javax.crypto.SecretKey;
import javax.net.ssl.SSLEngine;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.Provider;
import java.security.Security;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.apache.pulsar.client.impl.PulsarChannelInitializer.TLS_HANDLER;

/**
 * PSK client.
 */
@Slf4j
public class PSKClient extends ChannelInitializer<SocketChannel> {

    @Override
    public void initChannel(SocketChannel ch) throws Exception {
        ch.pipeline().addLast(TLS_HANDLER, new SslHandler(createSSLEngine(ch)));
        ch.pipeline().addLast("decoder", new MqttDecoder());
        ch.pipeline().addLast("encoder", MqttEncoder.INSTANCE);
        ch.pipeline().addLast("handler", new PSKInboundHandler());
    }

    class PSKInboundHandler extends ChannelInboundHandlerAdapter{

        public void channelActive(ChannelHandlerContext ctx) {
            log.info("channelActive id : {}", ctx.channel().id());
        }
    }

    public static SSLEngine createSSLEngine(SocketChannel ch) throws Exception{

        Provider provider = new OpenSSLProvider();

        PSKKeyManager myPskKeyManager = new PSKKeyManager() {
            @Override
            public String chooseServerKeyIdentityHint(Socket socket) {
                return "alpha";
            }

            @Override
            public String chooseServerKeyIdentityHint(SSLEngine engine) {
                return "alpha";
            }

            @Override
            public String chooseClientKeyIdentity(String identityHint, Socket socket) {
                return "lbstest";
            }

            @Override
            public String chooseClientKeyIdentity(String identityHint, SSLEngine engine) {
                log.info("chooseClientKeyIdentity2 identityHint :{}", identityHint);
                return "lbstest";
            }

            @Override
            public SecretKey getKey(String identityHint, String identity, Socket socket) {
                log.info("getKey identityHint1 :{}, identity :{}", identityHint, identity);
                return new SecretKey() {
                    @Override
                    public String getAlgorithm() {
                        return "PSK";
                    }

                    @Override
                    public String getFormat() {
                        return "RAW";
                    }

                    @Override
                    public byte[] getEncoded() {
                        return BinaryCodec.fromAscii("ruckus123!".toCharArray());
                    }
                };
            }

            @Override
            public SecretKey getKey(String identityHint, String identity, SSLEngine engine) {
                log.info("getKey identityHint2 :{}, identity :{}", identityHint, identity);
                return new SecretKey() {
                    @Override
                    public String getAlgorithm() {
                        return null;
                    }

                    @Override
                    public String getFormat() {
                        return null;
                    }

                    @Override
                    public byte[] getEncoded() {
                        return "ruckus123!".getBytes(StandardCharsets.UTF_8);
                    }
                };
            }
        };

        List<String> applicationProtocols = new ArrayList<>();
        applicationProtocols.add(ApplicationProtocolNames.HTTP_2);
        applicationProtocols.add(ApplicationProtocolNames.HTTP_1_1);
        applicationProtocols.add(ApplicationProtocolNames.SPDY_1);
        applicationProtocols.add(ApplicationProtocolNames.SPDY_2);
        applicationProtocols.add(ApplicationProtocolNames.SPDY_3);
        applicationProtocols.add(ApplicationProtocolNames.SPDY_3_1);
        List<String> ciphers = new ArrayList<>();
        ciphers.add("PSK-AES128-CBC-SHA");
        //

        ApplicationProtocolConfig protocolConfig = new ApplicationProtocolConfig(
                ApplicationProtocolConfig.Protocol.ALPN,
                ApplicationProtocolConfig.SelectorFailureBehavior.CHOOSE_MY_LAST_PROTOCOL,
                ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT,
                applicationProtocols);

        List<String> protocols = new ArrayList<>();
        protocols.add("TLSv1.2");
        protocols.add("TLSv1.1");
        protocols.add("TLSv1");
        protocols.add("SSLv3");

        SslContext sslContext = SslContextBuilder.forClient()
                .keyManager(myPskKeyManager)
                .sslProvider(SslProvider.JDK)
                .sslContextProvider(provider)
                .applicationProtocolConfig(protocolConfig)
                .protocols(protocols)
                .ciphers(ciphers)
                .build();
        SSLEngine sslEngine = sslContext.newEngine(ch.alloc());
        sslEngine.setUseClientMode(true);
        return sslEngine;
    }

    public static void main(String[] args) throws Exception{
        Bootstrap client = new Bootstrap();

        EventLoopGroup group = new NioEventLoopGroup();
        client.group(group);
        client.channel(NioSocketChannel.class);
        client.handler(new PSKClient());
        CountDownLatch latch = new CountDownLatch(1);
        client.connect("localhost", 8883).addListener((ChannelFutureListener) future -> {
            log.info("connected result : {}", future.isSuccess());
        });
        latch.await(10, TimeUnit.MINUTES);
    }
}

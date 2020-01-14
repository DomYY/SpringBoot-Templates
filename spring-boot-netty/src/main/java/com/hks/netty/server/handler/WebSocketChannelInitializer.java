package com.hks.netty.server.handler;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.handler.stream.ChunkedWriteHandler;
import org.springframework.stereotype.Component;

@Component
public class WebSocketChannelInitializer extends ChannelInitializer<NioSocketChannel> {

    private static final StringDecoder DECODER = new StringDecoder();
    private static final StringEncoder ENCODER = new StringEncoder();

    @Override
    protected void initChannel(NioSocketChannel channel) throws Exception {
        ChannelPipeline pipeline = channel.pipeline();

        // 添加 HttpServerCodec 将请求和应答消息编码或解码为HTTP 消息
        pipeline.addLast(new HttpServerCodec());

        // 增加HttpObjectAggregator ，将HTTP 消息的多个部分组合成一条完整的HTTP消息
        pipeline.addLast(new HttpObjectAggregator(65536));

        // 添加ChunkedWriteHandler，来向客户端发送HTML5文件，主要用于支持浏览器和服务端进行WebSocket 通信
        pipeline.addLast("http-chunked", new ChunkedWriteHandler());

        // 增加WebSocket 服务端的handler
        pipeline.addLast(new WebSocketHandler());
    }
}
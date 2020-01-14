package com.hks.netty.server.handler;

import com.alibaba.fastjson.JSON;
import com.hks.netty.config.NettyConfig;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.util.CharsetUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 接收/处理/响应客户端websocket请求的核心业务处理类
 */
@Slf4j
@RequiredArgsConstructor
@ChannelHandler.Sharable
@Component
public class WebSocketHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private WebSocketServerHandshaker webSocketServerHandshaker;
    private static final String WEB_SOCKET_URL = "ws://localhost:8888/websocket";

    private StringBuffer stringBuffer = new StringBuffer();

    /**
     * 客户端与服务端创建连接的时候调用
     *
     * @param ctx
     * @throws Exception
     */
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        NettyConfig.group.add(ctx.channel());
        log.info("客户端与服务端连接开启...");
    }

    /**
     * 客户端与服务端断开连接的时候调用
     *
     * @param ctx
     * @throws Exception
     */
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        NettyConfig.group.remove(ctx.channel());
        log.info("客户端与服务端连接关闭...");
    }

    /**
     * 服务端接收客户端发送过来的数据结束之后调用
     *
     * @param ctx
     * @throws Exception
     */
    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        ctx.flush();
    }

    /**
     * 工程出现异常的时候调用
     *
     * @param ctx
     * @param cause
     * @throws Exception
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        cause.printStackTrace();
        ctx.close();
    }

    /**
     * 服务端处理客户端websocket请求的核心方法
     *
     * @param context
     * @param fullHttpRequest
     * @throws Exception
     */
    @Override
    protected void messageReceived(ChannelHandlerContext context, FullHttpRequest fullHttpRequest) throws Exception {
        //处理客户端向服务端发起http握手请求的业务
        handleHttpRequest(context, fullHttpRequest);
    }

    /**
     * 处理客户端与服务端之前的websocket业务
     *
     * @param ctx
     * @param frame
     */
    private void handleWebSocketFrame(ChannelHandlerContext ctx, WebSocketFrame frame) {
        //判断是否是关闭websocket的指令
        if (frame instanceof CloseWebSocketFrame) {
            webSocketServerHandshaker.close(ctx.channel(), (CloseWebSocketFrame) frame.retain());
        }
        //判断是否是ping消息
        if (frame instanceof PingWebSocketFrame) {
            ctx.channel().write(new PongWebSocketFrame(frame.content().retain()));
            return;
        }

        // 文本内容
        if (frame instanceof TextWebSocketFrame) {
            String body = ((TextWebSocketFrame) frame).text();
            if (!frame.isFinalFragment()) {
                stringBuffer.append(body);
            } else {
                handleMsg(ctx, body);
            }
        } else if (frame instanceof ContinuationWebSocketFrame) {
            String halfBody = ((ContinuationWebSocketFrame) frame).text();
            stringBuffer.append(halfBody);
            if (frame.isFinalFragment()) {
                handleMsg(ctx, stringBuffer.toString());
                stringBuffer = new StringBuffer();
            }
        }
    }

    private void handleMsg(ChannelHandlerContext ctx, String body) {
        try {
            if (!JSON.isValid(body)) {
                throw new RuntimeException("json 校验不通过");
            }
            log.info("json 格式校验通过");
        } catch (Exception e) {
            log.error("get json error :{}", body);
            return;
        }

        //log.info("服务端收到客户端的消息====>>>" + stringBuffer.toString());
        TextWebSocketFrame tws = new TextWebSocketFrame(body);
        //服务端向每个连接上来的客户端群发消息
        NettyConfig.group.writeAndFlush(tws);
    }

    /**
     * 处理客户端向服务端发起http握手请求的业务
     *
     * @param ctx
     * @param req
     */
    private void handleHttpRequest(ChannelHandlerContext ctx, FullHttpRequest req) {
        if (!req.getDecoderResult().isSuccess() || !("websocket".equals(req.headers().get("Upgrade")))) {
            sendHttpResponse(ctx, req, new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_REQUEST));
            return;
        }
        WebSocketServerHandshakerFactory wsFactory = new WebSocketServerHandshakerFactory(WEB_SOCKET_URL, null, false);
        WebSocketServerHandshaker webSocketServerHandshaker = wsFactory.newHandshaker(req);
        if (webSocketServerHandshaker == null) {
            WebSocketServerHandshakerFactory.sendUnsupportedWebSocketVersionResponse(ctx.channel());
        } else {
            ChannelPipeline pipeline = ctx.pipeline();
            pipeline.remove(ctx.name());
            pipeline.addLast(new SimpleChannelInboundHandler<WebSocketFrame>() {
                @Override
                protected void messageReceived(ChannelHandlerContext context, WebSocketFrame webSocketFrame) throws Exception {
                    handleWebSocketFrame(context, webSocketFrame);
                }
            });
            webSocketServerHandshaker.handshake(ctx.channel(), req);
        }
    }

    /**
     * 服务端向客户端响应消息
     *
     * @param ctx
     * @param req
     * @param res
     */
    private void sendHttpResponse(ChannelHandlerContext ctx, FullHttpRequest req, DefaultFullHttpResponse res) {
        if (res.getStatus().code() != 200) {
            ByteBuf buf = Unpooled.copiedBuffer(res.getStatus().toString(), CharsetUtil.UTF_8);
            res.content().writeBytes(buf);
            buf.release();
        }
        //服务端向客户端发送数据
        ChannelFuture f = ctx.channel().writeAndFlush(res);
        if (res.getStatus().code() != 200) {
            f.addListener(ChannelFutureListener.CLOSE);
        }
    }
}
package p1.utils.json;

import dev.langchain4j.internal.Json;
import dev.langchain4j.spi.json.JsonCodecFactory;

public class TolerantJsonCodecFactory implements JsonCodecFactory {

    @Override
    public Json.JsonCodec create() {
        // LangChain4j 启动时会通过 ServiceLoader 读取这个工厂，
        // 然后把返回的 JsonCodec 注册成全局 JSON 编解码器。
        return new TolerantJsonCodec();
    }
}

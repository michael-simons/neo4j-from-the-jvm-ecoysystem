package org.neo4j.examples.jvm.helidon.se.reactive.support;

import io.helidon.common.GenericType;
import io.helidon.common.http.DataChunk;
import io.helidon.common.http.MediaType;
import io.helidon.common.reactive.Multi;
import io.helidon.media.common.MessageBodyStreamWriter;
import io.helidon.media.common.MessageBodyWriterContext;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Flow;

import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;

/**
 * Unrelated to Neo4j, streaming JSONB is not yet supported in Helidon.
 * See https://github.com/oracle/helidon/issues/1794
 */
public final class SseJsonObjectBodyStreamWriter implements MessageBodyStreamWriter<Object> {

	private static final MediaType TEXT_EVENT_STREAM_JSON = MediaType.parse("text/event-stream");
	private static final Jsonb JSON_FACTORY = JsonbBuilder.create();
	private static final byte[] DATA = "data: ".getBytes(StandardCharsets.UTF_8);
	private static final byte[] NL = "\n\n".getBytes(StandardCharsets.UTF_8);

	@Override
	public PredicateResult accept(GenericType<?> type, MessageBodyWriterContext context) {
		return PredicateResult.supports(Object.class, type);
	}

	@Override
	public Flow.Publisher<DataChunk> write(Flow.Publisher<? extends Object> publisher,
		GenericType<? extends Object> type, MessageBodyWriterContext context) {

		context.contentType(TEXT_EVENT_STREAM_JSON);
		return Multi.defer(() -> publisher)
			.flatMapIterable(m -> List.of(
				DataChunk.create(DATA),
				DataChunk.create(JSON_FACTORY.toJson(m).getBytes(StandardCharsets.UTF_8)),
				DataChunk.create(NL))
			);
	}
}

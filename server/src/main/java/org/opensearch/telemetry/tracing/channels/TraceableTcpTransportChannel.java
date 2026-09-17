/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package org.opensearch.telemetry.tracing.channels;

import org.opensearch.Version;
import org.opensearch.core.transport.TransportResponse;
import org.opensearch.telemetry.tracing.Span;
import org.opensearch.telemetry.tracing.SpanScope;
import org.opensearch.telemetry.tracing.Tracer;
import org.opensearch.transport.BaseTcpTransportChannel;
import org.opensearch.transport.TcpTransportChannel;
import org.opensearch.transport.TransportChannel;

import java.io.IOException;
import java.util.Optional;

/**
 * Tracer wrapped {@link TransportChannel}
 */
public class TraceableTcpTransportChannel extends BaseTcpTransportChannel {

    private final TransportChannel delegate;
    private final Span span;
    private final Tracer tracer;

    /**
     * Constructor.
     * @param delegate delegate
     * @param span span
     * @param tracer tracer
     */
    public TraceableTcpTransportChannel(TcpTransportChannel delegate, Span span, Tracer tracer) {
        super(delegate.getChannel());
        this.delegate = delegate;
        this.span = span;
        this.tracer = tracer;
    }

    /**
     * Factory method. The span is ended when the response (or the error response) is sent; nothing is attached to
     * the underlying {@link org.opensearch.transport.TcpChannel}, which is long-lived and shared by all requests
     * received over the connection.
     *
     * @param delegate delegate
     * @param span     span
     * @param tracer tracer
     * @return transport channel
     */
    public static TransportChannel create(TcpTransportChannel delegate, final Span span, final Tracer tracer) {
        if (tracer.isRecording() == true) {
            return new TraceableTcpTransportChannel(delegate, span, tracer);
        } else {
            return delegate;
        }
    }

    @Override
    public String getProfileName() {
        return delegate.getProfileName();
    }

    @Override
    public String getChannelType() {
        return delegate.getChannelType();
    }

    @Override
    public void sendResponse(TransportResponse response) throws IOException {
        try (SpanScope scope = tracer.withSpanInScope(span)) {
            delegate.sendResponse(response);
        } catch (final IOException ex) {
            span.setError(ex);
            throw ex;
        } finally {
            span.endSpan();
        }
    }

    public void sendResponseBatch(TransportResponse response) {
        try (SpanScope scope = tracer.withSpanInScope(span)) {
            delegate.sendResponseBatch(response);
        } finally {
            span.endSpan();
        }
    }

    public void completeStream() {
        try (SpanScope scope = tracer.withSpanInScope(span)) {
            delegate.completeStream();
        } finally {
            span.endSpan();
        }
    }

    @Override
    public void sendResponse(Exception exception) throws IOException {
        try (SpanScope scope = tracer.withSpanInScope(span)) {
            delegate.sendResponse(exception);
        } finally {
            span.setError(exception);
            span.endSpan();
        }
    }

    @Override
    public Version getVersion() {
        return delegate.getVersion();
    }

    @Override
    public <T> Optional<T> get(String name, Class<T> clazz) {
        return delegate.get(name, clazz);
    }
}

/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package org.opensearch.telemetry.tracing.channels;

import org.opensearch.core.transport.TransportResponse;
import org.opensearch.telemetry.tracing.Span;
import org.opensearch.telemetry.tracing.SpanScope;
import org.opensearch.telemetry.tracing.Tracer;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.transport.TcpChannel;
import org.opensearch.transport.TcpTransportChannel;
import org.opensearch.transport.TransportChannel;
import org.junit.Before;

import java.io.IOException;

import static org.hamcrest.Matchers.instanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link TraceableTcpTransportChannel}
 */
public class TraceableTcpTransportChannelTests extends OpenSearchTestCase {

    private TcpChannel tcpChannel;
    private TcpTransportChannel delegate;
    private Span span;
    private Tracer tracer;
    private SpanScope spanScope;

    @Before
    public void setup() {
        tcpChannel = mock(TcpChannel.class);
        delegate = mock(TcpTransportChannel.class);
        span = mock(Span.class);
        tracer = mock(Tracer.class);
        spanScope = mock(SpanScope.class);

        when(delegate.getChannel()).thenReturn(tcpChannel);
        when(tracer.isRecording()).thenReturn(true);
        when(tracer.withSpanInScope(span)).thenReturn(spanScope);
    }

    public void testCreateReturnsTracingChannelWhenRecording() {
        TransportChannel result = TraceableTcpTransportChannel.create(delegate, span, tracer);

        assertThat(result, instanceOf(TraceableTcpTransportChannel.class));
    }

    public void testCreateReturnsDelegateWhenNotRecording() {
        when(tracer.isRecording()).thenReturn(false);

        TransportChannel result = TraceableTcpTransportChannel.create(delegate, span, tracer);

        assertSame(delegate, result);
        verify(tcpChannel, never()).addCloseListener(any());
    }

    public void testCreateDoesNotRegisterCloseListenerOnChannel() {
        // inter-node transport connections are long-lived: anything attached to the channel's close future is retained
        // until the connection closes, so per-request state must not be registered on the channel
        int requests = randomIntBetween(2, 100);
        for (int i = 0; i < requests; i++) {
            TraceableTcpTransportChannel.create(delegate, mock(Span.class), tracer);
        }

        verify(tcpChannel, never()).addCloseListener(any());
    }

    public void testSendResponseEndsSpanWithinScope() throws IOException {
        TransportChannel channel = TraceableTcpTransportChannel.create(delegate, span, tracer);
        TransportResponse response = mock(TransportResponse.class);

        channel.sendResponse(response);

        verify(delegate, times(1)).sendResponse(response);
        verify(tracer, times(1)).withSpanInScope(span);
        verify(spanScope, times(1)).close();
        verify(span, times(1)).endSpan();
    }

    public void testSendErrorResponseMarksSpanAsErrorAndEndsIt() throws IOException {
        TransportChannel channel = TraceableTcpTransportChannel.create(delegate, span, tracer);
        Exception exception = new IOException("boom");

        channel.sendResponse(exception);

        verify(delegate, times(1)).sendResponse(exception);
        verify(span, times(1)).setError(exception);
        verify(span, times(1)).endSpan();
    }
}

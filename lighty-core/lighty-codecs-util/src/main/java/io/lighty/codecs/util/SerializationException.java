/*
 * Copyright (c) 2021 PANTHEON.tech s.r.o. All Rights Reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v10.html
 */
package io.lighty.codecs.util;

/**
 * This exception should be thrown when serialization problem occurs.
 */
public class SerializationException extends Exception {
    private static final long serialVersionUID = 2053802415449540367L;

    public SerializationException(final Throwable cause) {
        super(cause);
    }

    public SerializationException(final String message, final Throwable cause) {
        super(message, cause);
    }
}

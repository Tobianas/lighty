/*
 * Copyright (c) 2021 PANTHEON.tech s.r.o. All Rights Reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v10.html
 */
package io.lighty.core.controller.impl.util;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.google.common.base.Preconditions;
import io.lighty.codecs.util.JsonNodeConverter;
import io.lighty.codecs.util.XmlNodeConverter;
import io.lighty.codecs.util.exception.DeserializationException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMDataTreeWriteTransaction;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.impl.ImmutableContainerNodeBuilder;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class FileToDatastoreUtils {

    private static final Logger LOG = LoggerFactory.getLogger(FileToDatastoreUtils.class);
    public static final long IMPORT_TIMEOUT_MILLIS = 20_000;

    private FileToDatastoreUtils() {
        throw new UnsupportedOperationException("Init of utility class is forbidden");
    }

    private static NormalizedNode wrapNodesWithContainer(final NormalizedNode node, final QName containerName) {
        Preconditions.checkState(node instanceof DataContainerChild, "Node is expected to be a DataContainerChild");
        return ImmutableContainerNodeBuilder.create()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(containerName))
                .addChild((DataContainerChild) node)
                .build();
    }

    /**
     * Writes/merges input stream containing serialized normalized node data into config datastore.
     *
     * @param inputStream            stream of serialized node to deserialize
     * @param yangInstanceIdentifier yang instance identifier of the node to deserialize
     * @param fileFormat             format of file (XML/JSON)
     * @param effectiveModelContext  current model context
     * @param dataBroker             dataBroker
     * @param override               override current data present in config datastore
     *                               (true = PUT, false = MERGE)
     * @throws IOException              if something goes wrong with file (not found, corrupted etc..)
     * @throws DeserializationException if deserialization of file data to normalized node fails
     * @throws InterruptedException     if interrupted while committing changes to datastore
     * @throws ExecutionException       if something goes wrong while committing changes to datastore
     * @throws TimeoutException         if something goes wrong while committing changes to datastore
     */
    public static void importConfigDataFile(final InputStream inputStream,
            final YangInstanceIdentifier yangInstanceIdentifier,
            final ImportFileFormat fileFormat, final EffectiveModelContext effectiveModelContext,
            final DOMDataBroker dataBroker, final boolean override)
            throws IOException, DeserializationException, InterruptedException, ExecutionException, TimeoutException {

        try (Reader inputReader = new InputStreamReader(inputStream, Charset.defaultCharset())) {
            NormalizedNode deserializedNode;
            if (fileFormat == ImportFileFormat.JSON) {
                // Json deserialization needs parent identifier
                final YangInstanceIdentifier parentIdentifier = yangInstanceIdentifier.getParent() != null
                        ? yangInstanceIdentifier.getParent()
                        : YangInstanceIdentifier.empty();
                deserializedNode = new JsonNodeConverter(effectiveModelContext)
                        .deserialize(parentIdentifier, inputReader);
                // JSON parser doesn't wrap deserialized top level NormalizedNode in root node
                // (urn:ietf:params:xml:ns:netconf:base:1.0)data as XML parser does, wrap it here
                if (parentIdentifier.isEmpty()) {
                    deserializedNode = wrapNodesWithContainer(deserializedNode, SchemaContext.NAME);
                }
            } else if (fileFormat == ImportFileFormat.XML) {
                deserializedNode = new XmlNodeConverter(effectiveModelContext)
                        .deserialize(yangInstanceIdentifier, inputReader);
            } else {
                throw new UnsupportedOperationException("Format of config data file is not recognized");
            }

            LOG.debug("Normalized nodes loaded from file {}: {}", inputStream, deserializedNode);
            writeNodes(deserializedNode, yangInstanceIdentifier, dataBroker, override);
        }
    }

    /**
     * Writes/merges input stream containing serialized normalized node data into config datastore.
     *
     * <p>
     * Node is written at root node, that means only top level nodes are supported.
     *
     * <p>
     * In the case of importing XML file, node needs to be wrapped in
     * {@code <data xmlns="urn:ietf:params:xml:ns:netconf:base:1.0">} element.
     *
     * @param inputStream           stream of serialized node to deserialize
     * @param fileFormat            format of file (XML/JSON)
     * @param effectiveModelContext current model context
     * @param dataBroker            dataBroker
     * @param override              override current data present in config datastore
     *                              (true = PUT, false = MERGE)
     * @throws IOException              if something goes wrong with file (not found, corrupted etc..)
     * @throws DeserializationException if deserialization of file data to normalized node fails
     * @throws InterruptedException     if interrupted while committing changes to datastore
     * @throws ExecutionException       if something goes wrong while committing changes to datastore
     * @throws TimeoutException         if something goes wrong while committing changes to datastore
     */
    public static void importConfigDataFile(final InputStream inputStream, final ImportFileFormat fileFormat,
            final EffectiveModelContext effectiveModelContext,
            final DOMDataBroker dataBroker, final boolean override)
            throws IOException, DeserializationException, InterruptedException, ExecutionException, TimeoutException {
        importConfigDataFile(inputStream, YangInstanceIdentifier.empty(), fileFormat, effectiveModelContext, dataBroker,
                override);
    }

    private static void writeNodes(final NormalizedNode nodes, final YangInstanceIdentifier instanceIdentifier,
            final DOMDataBroker dataBroker, final boolean override)
            throws InterruptedException, ExecutionException, TimeoutException {
        final DOMDataTreeWriteTransaction wrTrx = dataBroker.newWriteOnlyTransaction();
        if (override) {
            wrTrx.put(LogicalDatastoreType.CONFIGURATION, instanceIdentifier, nodes);
        } else {
            wrTrx.merge(LogicalDatastoreType.CONFIGURATION, instanceIdentifier, nodes);
        }
        wrTrx.commit().get(IMPORT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
    }

    public enum ImportFileFormat {
        JSON("json"),
        XML("xml");

        private String fileFormat;

        ImportFileFormat(String formatString) {
            this.fileFormat = formatString;
        }

        public String getFormatString() {
            return fileFormat;
        }

        @JsonCreator
        public static ImportFileFormat getFormatType(String inputtedFormat) {
            for (ImportFileFormat formatType : ImportFileFormat.values()) {
                if (formatType.fileFormat.equalsIgnoreCase(inputtedFormat)) {
                    return formatType;
                }
            }
            throw new IllegalStateException(String.format("Format %s is not supported, valid options: xml, json",
                    inputtedFormat));
        }
    }

}

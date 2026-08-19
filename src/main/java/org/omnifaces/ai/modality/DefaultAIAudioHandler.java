/*
 * Copyright OmniFaces
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.omnifaces.ai.modality;

import java.util.logging.Logger;

import org.omnifaces.ai.AIAudioHandler;
import org.omnifaces.ai.AIService;

/**
 * Default AI audio handler implementation that provides general-purpose prompt templates suitable for most current transcription-capable and
 * audio-generation-capable models.
 * <p>
 * This class is intended as a fallback when no provider-specific implementation is available.
 *
 * @author Bauke Scholtz
 * @since 1.1
 * @see AIAudioHandler
 * @see AIService
 */
public class DefaultAIAudioHandler implements AIAudioHandler {

    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new instance of this AI handler.
     */
    public DefaultAIAudioHandler() {
        //
    }

    /** Logger for current package. */
    protected static final Logger logger = Logger.getLogger(DefaultAIAudioHandler.class.getPackageName());

    @Override
    public String buildTranscribePrompt() {
        return """
                You are an expert at transcribing audio.
                Transcribe the audio verbatim in its original language.
                Preserve the spoken words exactly as heard.
                Rules:
                - Do not summarize or paraphrase.
                - Do not add explanations or notes.
                - Do not translate.
                - Omit non-speech sounds unless they are spoken words.
                - Do not include speaker labels.
                Output format:
                - Plain text transcription only.
                - No markdown or extra text.
            """;
    }

    /** The sample rate in Hz which a bare PCM audio response is assumed to carry: {@value}. */
    protected static final int PCM_WAV_SAMPLE_RATE = 24000;
    /** The number of channels which a bare PCM audio response is assumed to carry: {@value}. */
    protected static final int PCM_WAV_CHANNELS = 1;
    /** The bits per sample which a bare PCM audio response is assumed to carry: {@value}. */
    protected static final int PCM_WAV_BITS_PER_SAMPLE = 16;

    /**
     * Builds the 44-byte WAV header for the given amount of PCM content, which a provider emitting bare PCM needs prepended to make the result playable. The
     * parameters are the ones every current text-to-speech model emits: {@value #PCM_WAV_SAMPLE_RATE} Hz, {@value #PCM_WAV_CHANNELS} channel and
     * {@value #PCM_WAV_BITS_PER_SAMPLE} bits per sample.
     *
     * @param pcmContentLength The amount of PCM content in bytes.
     * @return The 44-byte WAV header.
     * @since 1.7
     * @see <a href="https://www.mmsp.ece.mcgill.ca/Documents/AudioFormats/WAVE/WAVE.html">WAV spec</a>
     */
    protected static byte[] createWavHeader(long pcmContentLength) {
        var header = new byte[44];
        var totalDataLength = pcmContentLength + 36;
        var byteRate = (long) PCM_WAV_SAMPLE_RATE * PCM_WAV_CHANNELS * PCM_WAV_BITS_PER_SAMPLE / 8;

        header[0] = 'R';
        header[1] = 'I';
        header[2] = 'F';
        header[3] = 'F';
        header[4] = (byte) (totalDataLength & 0xff);
        header[5] = (byte) ((totalDataLength >> 8) & 0xff);
        header[6] = (byte) ((totalDataLength >> 16) & 0xff);
        header[7] = (byte) ((totalDataLength >> 24) & 0xff);
        header[8] = 'W';
        header[9] = 'A';
        header[10] = 'V';
        header[11] = 'E';
        header[12] = 'f';
        header[13] = 'm';
        header[14] = 't';
        header[15] = ' ';
        header[16] = 16;
        header[17] = 0;
        header[18] = 0;
        header[19] = 0;
        header[20] = 1;
        header[21] = 0;
        header[22] = (byte) PCM_WAV_CHANNELS;
        header[23] = 0;
        header[24] = (byte) (PCM_WAV_SAMPLE_RATE & 0xff);
        header[25] = (byte) ((PCM_WAV_SAMPLE_RATE >> 8) & 0xff);
        header[26] = (byte) ((PCM_WAV_SAMPLE_RATE >> 16) & 0xff);
        header[27] = (byte) ((PCM_WAV_SAMPLE_RATE >> 24) & 0xff);
        header[28] = (byte) (byteRate & 0xff);
        header[29] = (byte) ((byteRate >> 8) & 0xff);
        header[30] = (byte) ((byteRate >> 16) & 0xff);
        header[31] = (byte) ((byteRate >> 24) & 0xff);
        header[32] = (byte) (PCM_WAV_CHANNELS * PCM_WAV_BITS_PER_SAMPLE / 8);
        header[33] = 0;
        header[34] = (byte) PCM_WAV_BITS_PER_SAMPLE;
        header[35] = 0;
        header[36] = 'd';
        header[37] = 'a';
        header[38] = 't';
        header[39] = 'a';
        header[40] = (byte) (pcmContentLength & 0xff);
        header[41] = (byte) ((pcmContentLength >> 8) & 0xff);
        header[42] = (byte) ((pcmContentLength >> 16) & 0xff);
        header[43] = (byte) ((pcmContentLength >> 24) & 0xff);

        return header;
    }

}

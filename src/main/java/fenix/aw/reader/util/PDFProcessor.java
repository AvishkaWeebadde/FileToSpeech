package fenix.aw.reader.util;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class PDFProcessor
{

    public static String extractTextFromPDF(String filePath) throws IOException
    {
        File file = new File(filePath);

        // check if the file exists
        if(!file.exists())
        {
            throw  new IOException("File not found: " + filePath);
        }

        // load the PDF
        try(PDDocument document = PDDocument.load(file))
        {
            if(document.isEncrypted())
            {
                throw new IOException("Cannot process encrypted files.");
            }

            PDFTextStripper textStripper = new PDFTextStripper();
            return textStripper.getText(document);
        }
    }

    public List<String> splitPdfIntoChunks(File pdfFile, int maxCharactersPerChunk) throws Exception {
        PDDocument document = PDDocument.load(pdfFile);
        PDFTextStripper pdfStripper = new PDFTextStripper();

        String fullText = pdfStripper.getText(document);
        document.close();

        List<String> chunks = new ArrayList<>();
        int start = 0;

        while (start < fullText.length()) {
            int end = Math.min(start + maxCharactersPerChunk, fullText.length());
            chunks.add(fullText.substring(start, end));
            start = end;
        }

        return chunks;
    }
}

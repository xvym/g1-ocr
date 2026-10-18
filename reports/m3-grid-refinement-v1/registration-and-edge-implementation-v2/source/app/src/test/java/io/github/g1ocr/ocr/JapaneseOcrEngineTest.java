package io.github.g1ocr.ocr;
import io.github.g1ocr.capture.SourceFrame;
import org.junit.Test;
import static org.junit.Assert.*;

public class JapaneseOcrEngineTest {
    @Test public void imagePipelineOutputsItsOwnTextAndRegionCoordinates(){
        BoxTextDetectorTest scene=new BoxTextDetectorTest();JapaneseOcrEngine.Result r=new JapaneseOcrEngine(scene.bank()).process(scene.source(false,false,false));
        assertEquals(1,r.rawRegions.size());assertEquals("A B",r.rawRegions.get(0).text);assertEquals("dialogue",r.rawRegions.get(0).role);
        assertArrayEquals(new double[]{1,14,4,15},r.rawRegions.get(0).gridRect,0);assertArrayEquals(new int[]{16,224,64,240},r.rawRegions.get(0).sourceRect);
        assertTrue(r.visibleState.cursor.downArrowVisible);
    }
    @Test public void failedFrameClearsPriorImageTextInsteadOfReusingIt(){
        BoxTextDetectorTest scene=new BoxTextDetectorTest();JapaneseOcrEngine engine=new JapaneseOcrEngine(scene.bank());engine.process(scene.source(false,false,false));
        JapaneseOcrEngine.Result invalid=engine.process(new SourceFrame(new byte[320*288*3],320,288,1,1,1));
        assertTrue(invalid.rawRegions.isEmpty());assertEquals("black_frame",invalid.visibleState.kind);assertFalse(invalid.visibleState.cursor.downArrowVisible);
    }
}

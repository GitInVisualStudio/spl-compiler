package de.thm.mni.compilerbau.phases._05_varalloc;

import de.thm.mni.compilerbau.utils.NotImplemented;

/**
 * This class describes the stack frame layout of a procedure.
 * It contains the sizes of the various subareas and provides methods to retrieve information about the stack frame required to generate code for the procedure.
 */
public class StackLayout {
    // The following values have to be set in phase 5
    public Integer argumentAreaSize = null;
    public Integer localVarAreaSize = null;
    public Integer outgoingAreaSize = null;
    public boolean isOptimizedLeafProcedure = false;  // Only relevant for --leafProc

    /**
     * @return The total size of the stack frame described by this object.
     */
    public int frameSize() {
        if (outgoingAreaSize == -1) {
            return localVarAreaSize + 4; // ohne RETURN-alt
        }
        return localVarAreaSize + outgoingAreaSize + 8; // mit RETURN-alt
    }

    /**
     * @return The offset (starting from the new stack pointer) where the old frame pointer is stored in this stack frame.
     */
    public int oldFramePointerOffset() {
        return outgoingAreaSize + 4;
    }

    /**
     * @return The offset (starting from the new frame pointer) where the old return address is stored in this stack frame.
     */
    public int oldReturnAddressOffset() {
        return -(this.localVarAreaSize + 4 /*old FP*/ + 4 /*Old Return*/);
    }
}

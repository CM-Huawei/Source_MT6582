

;/* NOTE: The following need to be kept in sync with parser.h */
;
;#define YDELAYA        200
;#define YDELAYB        168
;#define XDELAYA        136
;#define XDELAYB        104
;#define YADAPTCOEFFSA   72
;#define XADAPTCOEFFSA   56
;#define YADAPTCOEFFSB   40
;#define XADAPTCOEFFSB   20
;
;/* struct predictor_t members: */
;#define buf              0    /* int32_t* buf */
;#define YlastA           4    /* int32_t YlastA; */
;#define XlastA           8    /* int32_t XlastA; */
;
;#define YfilterB        12    /* int32_t YfilterB; */
;#define XfilterA        16    /* int32_t XfilterA; */
;
;#define XfilterB        20    /* int32_t XfilterB; */
;#define YfilterA        24    /* int32_t YfilterA; */
;	
;#define YcoeffsA        28    /* int32_t YcoeffsA[4]; */
;#define XcoeffsA        44    /* int32_t XcoeffsA[4]; */
;#define YcoeffsB        60    /* int32_t YcoeffsB[5]; */
;#define XcoeffsB        80    /* int32_t XcoeffsB[5]; */
;
;#define historybuffer  100    /* int32_t historybuffer[] */


   

; Macro for loading 2 registers, for various ARM versions.
; Registers must start with an even register, and must be consecutive.
;define ARM_ARCH 6

;         MACRO
;{$label}  macroname{$cond} {$parameter{,$parameter}...}
;          ; code
;          MEND

;.macro $LDR2OFS reg1, reg2, base, offset
;#if ARM_ARCH >= 6
    MACRO 
    INITIALIZATION
    lcla YDELAYA 
YDELAYA   seta 200
    lcla YDELAYB 
YDELAYB    seta 168
    lcla XDELAYA 
XDELAYA    seta 136
    lcla XDELAYB 
XDELAYB    seta 104
    lcla YADAPTCOEFFSA 
YADAPTCOEFFSA   seta 72
    lcla XADAPTCOEFFSA 
XADAPTCOEFFSA    seta 56
    lcla YADAPTCOEFFSB 
YADAPTCOEFFSB    seta 40        
    lcla XADAPTCOEFFSB 
XADAPTCOEFFSB    seta 20    
    
    lcla buf     
    lcla YlastA 
YlastA    seta 4
    lcla XlastA 
XlastA    seta 8
    lcla YfilterB 
YfilterB    seta 12
    lcla XfilterA 
XfilterA    seta 16
    lcla XfilterB 
XfilterB    seta 20
    lcla YfilterA 
YfilterA    seta 24        
    lcla YcoeffsA 
YcoeffsA   seta 28
    lcla XcoeffsA 
XcoeffsA    seta 44
    lcla YcoeffsB 
YcoeffsB   seta 60
    lcla XcoeffsB 
XcoeffsB    seta 80        
    lcla historybuffer 
historybuffer    seta 100
    MEND 

    MACRO   
    LDR2OFS $reg1,$reg2,$base,$offset
    ldrd    reg1,reg2,[ base, offset]
    MEND
;#else /* ARM_ARCH < 6 */
;#ifdef CPU_ARM7TDMI
;    add     \reg1, \base, \offset
;    ldmia   \reg1, {\reg1, \reg2}
;#else /* ARM9 (v4 and v5) is faster this way */
;    ldr     \reg1, [\base, \offset]
;    ldr     \reg2, [\base, \offset+4]
;#endif
;#endif /* ARM_ARCH */
;.endm

 
;; Macro for storing 2 registers, for various ARM versions.
;; Registers must start with an even register, and must be consecutive.

;.macro STR2OFS reg1, reg2, base, offset
;#if ARM_ARCH >= 6
;    strd    \reg1, [\base, \offset]
;#else
;    str     \reg1, [\base, \offset]
;    str     \reg2, [\base, \offset+4]
;#endif
;.endm
    MACRO   
$label STR2OFS $reg1,$reg2,$base,$offset
    strd    reg1,reg2,[ base, offset]
    MEND
    
	AREA |s1.text|, CODE, READONLY                      
    EXPORT predictor_decode_stereo_asm                  
    ; int unaligned_dot_and_sub_16(short *x, short *c, short *v)                  
   
    ;.global     predictor_decode_stereo
    ;.type       predictor_decode_stereo,%function

;; Register usage:
;;
;; r0-r11 - scratch
;; r12 - struct predictor_t* p
;; r14 - int32_t* p->buf

;; void predictor_decode_stereo(struct predictor_t* p,
;;                              int32_t* decoded0,
;;                              int32_t* decoded1,
;;                              int count)

; YC_TEST
; r0 = p
; definition :
YlastA      RN 11 ;  r11 :=p->YlastA
p           RN 12 ;  r12 := p
p_buf       RN 14 ;  r14 := p->buf
YDELAYA_3   RN 2  ;  r2 := p->buf[YDELAYA-3]
YDELAYA_2   RN 3  ;  r3 := p->buf[YDELAYA-2]
YDELAYA_1   RN 10 ;  r10 := p->buf[YDELAYA-1]
YDELAYA_0      RN 11 ;  r11 :=p->buf[YDELAYA]= p->YlastA
YcoeffsA_0  RN 6  ;  r6 := p->YcoeffsA[0]  
YcoeffsA_1  RN 7  ;  r7 := p->YcoeffsA[1]  
YcoeffsA_2  RN 8  ;  r8 := p->YcoeffsA[2]  
YcoeffsA_3  RN 9  ;  r9 := p->YcoeffsA[3]  

predictor_decode_stereo_asm
    
    stmfd   sp!, {r1-r11, lr}
    
;    ; r1 (decoded0) is [sp]
;    ; r2 (decoded1) is [sp, #4]
;    ; r3 (count)    is [sp, #8]

   
    ldr     r14, [r0]     ;; r14 := p->buf
    mov     p , r0       ;; r12 := p
loop

;;;;;;;;;;;;;;;;;;;;;;;;;;;; PREDICTOR Y

;; Predictor Y, Filter A
    
    add     r2, p_buf, #188                   ; r2 := &p->buf[YDELAYA-3]
    add     r5, p, #28                               ; r5 := p->YcoeffsA[0]      
    ldmia   r2, {r2, r3, r10}    ; r2 := p->buf[YDELAYA-3], r3 := p->buf[YDELAYA-2],r10 := p->buf[YDELAYA-1]                                                      
    ldmia   r5!, {r6 ,r7}            ; r6 := p->YcoeffsA[0],r7 := p->YcoeffsA[1]
    ldr     r11,[r12,#4]                
    ldmia   r5!, {r8,r9}       ; r8 := p->YcoeffsA[2],r9 := p->YcoeffsA[3]                                                                                      
    subs    r10, r11, r10                   ; r10 := r11 - r10 = YlastA - YDELAYA_1
    mul     r0, r11, r6                 ; r0 += p->buf[YDELAYA] * p->YcoeffsA[0]    
    strd    r10, r11, [p_buf,#196]                      ; p->buf[YDELAYA-1] = r10,p->buf[YDELAYA] = r11   
    
    mla     r0, r10, r7, r0            ; r0 += p->buf[YDELAYA-1] * p->YcoeffsA[1]
    ; flags were set above, in the subs instruction
    mvngt   r10, #0
    mla    r0, r3, r8,r0             ; r0 += p->buf[YDELAYA-2] * p->YcoeffsA[2]
    movlt   r10, #1                                  ; r10 := SIGN(r10) (see .c for SIGN macro)
    mla     r0, r2, r9,r0                ; r0 := p->buf[YDELAYA-3] * p->YcoeffsA[3]
        

    cmp     r11, #0
    mvngt   r11, #0
    movlt   r11, #1                                  ; r11 := SIGN(r11) (see .c for SIGN macro)

    strd r10, r11, [p_buf, #68]                      ; p->buf[YADAPTCOEFFSA-1] := r10, p->buf[YADAPTCOEFFSA] := r11

    ;; NOTE: r0 now contains predictionA - don't overwrite.

;; Predictor Y, Filter B
    ldrd    r6,r7,[p,#12]                           ; r6 := p->YfilterB,r7 := p->XfilterA
    add     r2, p_buf, #152                            ; r2 := &p->buf[YDELAYB-4]    
    rsb     r6, r6, r6, lsl #5                       ; r6 := r6 * 32 - r6 ( == r6*31)
    ldmia   r2, {r2 - r4, r10}                       ; r2 := p->buf[YDELAYB-4],r3 := p->buf[YDELAYB-3],r4 := p->buf[YDELAYB-2],r10 := p->buf[YDELAYB-1]    
    sub     r11, r7, r6, asr #5                      ; r11 (p->buf[YDELAYB]) := r7 - (r6 >> 5)
    add     r9, r12, #60                             ; r5 point to YcoeffsB
    str     r7, [p, #12]                             ; p->YfilterB := r7 (p->XfilterA)    
    ldmia   r9, {r5 - r9}                            ; r5 := p->YcoeffsB[0],r6 := p->YcoeffsB[1],r7 := p->YcoeffsB[2],
                                                     ; r8 := p->YcoeffsB[3] ,r9 := p->YcoeffsB[4]
    subs    r10, r11, r10                            ; r10 := r11 - r10
    strd    r10, r11, [p_buf, #164]                  ; p->buf[YDELAYB-1] = r10, p->buf[YDELAYB] = r11
    mul     r1, r10, r6                              ; r1 += p->buf[YDELAYB-1] * p->YcoeffsB[1]
    ; flags were set above, in the subs instruction    
    mvngt   r10, #0
    mla     r1, r11, r5,r1                              ; r1 := p->buf[YDELAYB] * p->YcoeffsB[0]
    movlt   r10, #1                                  ; r10 := SIGN(r10) (see .c for SIGN macro)
    mla     r1, r4, r7, r1                           ; r1 += p->buf[YDELAYB-2] * p->YcoeffsB[2]
    cmp     r11, #0
    mla     r1, r3, r8, r1                           ; r1 += p->buf[YDELAYB-3] * p->YcoeffsB[3]
    mvngt   r11, #0
    mla     r1, r2, r9, r1                           ; r1 += p->buf[YDELAYB-4] * p->YcoeffsB[4]                
    movlt   r11, #1                                  ; r11 := SIGN(r11) (see .c for SIGN macro)

    strd    r10, r11, [p_buf, #36]                   ; p->buf[YADAPTCOEFFSB-1] := r10, p->buf[YADAPTCOEFFSB] := r11

   ;; r0 still contains predictionA
   ;; r1 contains predictionB
   ;
   ;; Finish Predictor Y

    ldr     r2, [sp]                                 ; r2 := decoded0
    ldr     r4, [p, #24]                             ; r4 := p->YfilterA
    add     r0, r0, r1, asr #1                       ; r0 := r0 + (r1 >> 1)    
    ldr     r3, [r2]                                 ; r3 := *decoded0
    rsb     r4, r4, r4, lsl #5                       ; r4 := r4 * 32 - r4 ( == r4*31)
    add     r1, r3, r0, asr #10                      ; r1 := r3 + (r0 >> 10)
    str     r1, [p, #4]                              ; p->YlastA := r1
    add     r1, r1, r4, asr #5                       ; r1 := r1 + (r4 >> 5)
    str     r1, [p, #24]                             ; p->YfilterA := r1

    ;; r1 contains p->YfilterA
    ;; r2 contains decoded0
    ;; r3 contains *decoded0
    ;
    ;; r5, r6, r7, r8, r9 contain p->YcoeffsB[0..4]
    ;; r10, r11 contain p->buf[YADAPTCOEFFSB-1] and p->buf[YADAPTCOEFFSB]

    str     r1, [r2], #4                            ; *(decoded0++) := r1  (p->YfilterA)
    str     r2, [sp]                                ; save decoded0
    cmp     r3, #0                  
    add     r2, p_buf, #24
    beq     third    
    ldmia   r2, {r2 - r4}                           ; r2 := p->buf[YADAPTCOEFFSB-4]
                                                    ; r3 := p->buf[YADAPTCOEFFSB-3]
                                                    ; r4 := p->buf[YADAPTCOEFFSB-2]
    blt     first

    ;; *decoded0 > 0
 
    sub     r5, r5, r11                             ; r5 := p->YcoeffsB[0] - p->buf[YADAPTCOEFFSB]
    sub     r6, r6, r10                             ; r6 := p->YcoeffsB[1] - p->buf[YADAPTCOEFFSB-1]
    sub     r9, r9, r2                              ; r9 := p->YcoeffsB[4] - p->buf[YADAPTCOEFFSB-4]
    sub     r8, r8, r3                              ; r8 := p->YcoeffsB[3] - p->buf[YADAPTCOEFFSB-3]
    add     r0, p, #60      
    sub     r7, r7, r4                              ; r7 := p->YcoeffsB[2] - p->buf[YADAPTCOEFFSB-2]   
    add     r1, p, #28
    stmia   r0, {r5 - r9}                           ; Save p->YcoeffsB[]
    add     r11, p_buf, #60    
    ldmia   r1, {r2 - r5}                           ; r2 := p->YcoeffsA[0], r3 := p->YcoeffsA[1]
                                                    ; r4 := p->YcoeffsA[2], r5 := p->YcoeffsA[3]
    ldmia   r11!, {r6,r7}                           ; r6 := p->buf[YADAPTCOEFFSA-3],r7 := p->buf[YADAPTCOEFFSA-2]
    ldmia   r11, {r8,r9}                                              ; r8 := p->buf[YADAPTCOEFFSA-1],r9 := p->buf[YADAPTCOEFFSA]
    sub     r5, r5, r6                              ; r5 := p->YcoeffsA[3] - p->buf[YADAPTCOEFFSA-3]                
    sub     r4, r4, r7                              ; r4 := p->YcoeffsA[2] - p->buf[YADAPTCOEFFSA-2]                                                                                                        
    sub     r3, r3, r8                              ; r3 := p->YcoeffsA[1] - p->buf[YADAPTCOEFFSA-1]                                                    
    sub     r2, r2, r9                              ; r2 := p->YcoeffsA[0] - p->buf[YADAPTCOEFFSA]                                                            
    b       second


first  ;; *decoded0 < 0
    add     r5, r5, r11                             ; r5 := p->YcoeffsB[0] + p->buf[YADAPTCOEFFSB]
    add     r6, r6, r10                             ; r6 := p->YcoeffsB[1] + p->buf[YADAPTCOEFFSB-1]
    add     r9, r9, r2                              ; r9 := p->YcoeffsB[4] + p->buf[YADAPTCOEFFSB-4]
    add     r8, r8, r3                              ; r9 := p->YcoeffsB[3] + p->buf[YADAPTCOEFFSB-3]
    add     r0, p, #60      
    add     r7, r7, r4                              ; r8 := p->YcoeffsB[2] + p->buf[YADAPTCOEFFSB-2]    
    add     r1, p, #28
    stmia   r0, {r5 - r9}                           ; Save p->YcoeffsB[]    
    add     r11, p_buf, #60
    ldmia   r1, {r2 - r5}                           ; r2 := p->YcoeffsA[0],r3 := p->YcoeffsA[1],r4 := p->YcoeffsA[2],r5 := p->YcoeffsA[3]                                        
    ldmia   r11!, {r6,r7}                           ; r6 := p->buf[YADAPTCOEFFSA-3], r7 := p->buf[YADAPTCOEFFSA-2]  
    ldmia   r11, {r8,r9}                                                ; r8 := p->buf[YADAPTCOEFFSA-1], r9 := p->buf[YADAPTCOEFFSA]
    add     r5, r5, r6                              ; r5 := p->YcoeffsA[3] + p->buf[YADAPTCOEFFSA-3]            
    add     r4, r4, r7                              ; r4 := p->YcoeffsA[2] + p->buf[YADAPTCOEFFSA-2]                                                        
    add     r3, r3, r8                              ; r3 := p->YcoeffsA[1] + p->buf[YADAPTCOEFFSA-1]                                                    
    add     r2, r2, r9                              ; r2 := p->YcoeffsA[0] + p->buf[YADAPTCOEFFSA]            
    
second
    stmia   r1, {r2 - r5}                           ; Save p->YcoeffsA

third

;;;;;;;;;;;;;;;;;;;;;;;;;;;; PREDICTOR X
;; Predictor X, Filter A
    add     r5, p, #44
    add     r2, p_buf, #124                         ; r2 := &p->buf[XDELAYA-3]    
    ldmia   r5!, {r6 ,r7 }                           ; r6 := p->XcoeffsA[0],r7 := p->XcoeffsA[1]
    ldr     r11, [p, #8]                            ; r11 := p->XlastA         
                                                        
    ldmia   r2, {r2, r3, r10}                       ; r2 := p->buf[XDELAYA-3]
                                                    ; r3 := p->buf[XDELAYA-2]
                                                    ; r10 := p->buf[XDELAYA-1]
   
    mul     r0, r11, r6                             ; r0 := p->buf[XDELAYA] * p->XcoeffsA[0]
    ldmia   r5, {r8 ,r9 }                           ; r8 := p->XcoeffsA[2],r9 := p->XcoeffsA[3]	                                                    
    subs    r10, r11, r10                           ; r10 := r11 - r10    
    strd    r10, r11, [p_buf, #132]                    ; p->buf[XDELAYA-1] = r10,p->buf[XDELAYA] = r11    
    mla     r0, r10, r7, r0                         ; r0 += p->buf[XDELAYA-1] * p->XcoeffsA[1]
    mvngt   r10, #0
    mla     r0, r3, r8, r0                          ; r0 += p->buf[XDELAYA-2] * p->XcoeffsA[2]
    movlt   r10, #1 
    mla     r0, r2, r9, r0                          ; r0 += p->buf[XDELAYA-3] * p->XcoeffsA[3]
    ;; flags were set above, in the subs instruction
   
                                    ; r10 := SIGN(r10) (see .c for SIGN macro)
    cmp     r11, #0
    mvngt   r11, #0
    movlt   r11, #1                                 ; r11 := SIGN(r11) (see .c for SIGN macro)
    strd    r10, r11, [r14, #52]                       ; p->buf[XADAPTCOEFFSA-1] := r10,p->buf[XADAPTCOEFFSA] := r11

; NOTE: r0 now contains predictionA - don't overwrite.
; Predictor X, Filter B
    add     r2, r14, #88                            ; r2 := &p->buf[XDELAYB-4]
    ldrd    r6, r7, [p, #20]                           ; r6 := p->XfilterB, r7 := p->YfilterA    
    ldmia   r2, {r2 - r4, r10}                      ; r2 := p->buf[XDELAYB-4],r3 := p->buf[XDELAYB-3]
                                                    ; r4 := p->buf[XDELAYB-2],r10 := p->buf[XDELAYB-1]                                    ;
    rsb     r6, r6, r6, lsl #5                      ; r6 := r2 * 32 - r6 ( == r6*31)
    add     r1, p , #80  
    sub     r11, r7, r6, asr #5                     ; r11 (p->buf[XDELAYB]) := r7 - (r6 >> 5)
    str     r7, [p, #20]                            ; p->XfilterB := r7 (p->YfilterA)        
    ldmia   r1!, {r5,r6}                           ; r5 := p->XcoeffsB[0],r6 := p->XcoeffsB[1],r7 := p->XcoeffsB[2]
                                                    ; r8 := p->XcoeffsB[3],r9 := p->XcoeffsB[4]
    subs    r10, r11, r10                           ; r10 := r11 - r10
    ldmia   r1!, {r7-r9}    
    strd r10, r11, [r14, #100]                      ; p->buf[XDELAYB-1] = r10,p->buf[XDELAYB] = r11

    mul     r1, r10, r6                         ; r1 += p->buf[XDELAYB-1] * p->XcoeffsB[1]
    ;; flags were set above, in the subs instruction
    mvngt   r10, #0    
    mla     r1, r11, r5 , r1                            ; r1 := p->buf[XDELAYB] * p->XcoeffsB[0]        
    movlt   r10, #1                                 ; r10 := SIGN(r10) (see .c for SIGN macro)    
    mla     r1, r4, r7, r1                          ; r1 += p->buf[XDELAYB-2] * p->XcoeffsB[2]
    cmp     r11, #0
    mla     r1, r3, r8, r1                          ; r1 += p->buf[XDELAYB-3] * p->XcoeffsB[3]
    mvngt   r11, #0
    mla     r1, r2, r9, r1                          ; r1 += p->buf[XDELAYB-4] * p->XcoeffsB[4]               
    movlt   r11, #1                                 ; r11 := SIGN(r11) (see .c for SIGN macro)
    strd r10, r11,[r14, #16]                        ; p->buf[XADAPTCOEFFSB-1] := r10,p->buf[XADAPTCOEFFSB] := r11

    ;; r0 still contains predictionA
    ;; r1 contains predictionB
    ;
    ;; Finish Predictor X
    ldr     r2, [sp, #4]                            ; r2 := decoded1
    ldr     r4, [r12, #16]                          ; r4 := p->XfilterA
    add     r0, r0, r1, asr #1                      ; r0 := r0 + (r1 >> 1)   
    ldr     r3, [r2]                                ; r3 := *decoded1
    rsb     r4, r4, r4, lsl #5                      ; r4 := r4 * 32 - r4 ( == r4*31)
    add     r1, r3, r0, asr #10                     ; r1 := r3 + (r0 >> 10)
    str     r1, [r12, #8]                           ; p->XlastA := r1
    add     r1, r1, r4, asr #5                      ; r1 := r1 + (r4 >> 5)
    str     r1, [r12, #16]                          ; p->XfilterA := r1

    ;; r1 contains p->XfilterA
    ;; r2 contains decoded1
    ;; r3 contains *decoded1
    ;
    ;; r5, r6, r7, r8, r9 contain p->XcoeffsB[0..4]
    ;; r10, r11 contain p->buf[XADAPTCOEFFSB-1] and p->buf[XADAPTCOEFFSB]
    str     r1, [r2], #4                           ; *(decoded1++) := r1  (p->XfilterA)
    str     r2, [sp, #4]                           ; save decoded1
    cmp     r3, #0
    add     r2, r14, #4
    beq     third_2    
    ldmia   r2, {r2 - r4}                          ; r2 := p->buf[XADAPTCOEFFSB-4],r3 := p->buf[XADAPTCOEFFSB-3]
                                                   ; r4 := p->buf[XADAPTCOEFFSB-2]
    blt     first_2
    
    ;; *decoded1 > 0
    
    sub     r5, r5, r11                            ; r5 := p->XcoeffsB[0] - p->buf[XADAPTCOEFFSB]
    
    sub     r6, r6, r10                            ; r6 := p->XcoeffsB[1] - p->buf[XADAPTCOEFFSB-1]
    sub     r9, r9, r2                             ; r9 := p->XcoeffsB[4] - p->buf[XADAPTCOEFFSB-4]
    sub     r8, r8, r3                             ; r8 := p->XcoeffsB[3] - p->buf[XADAPTCOEFFSB-3]
    sub     r7, r7, r4                             ; r7 := p->XcoeffsB[2] - p->buf[XADAPTCOEFFSB-2]
    add     r0, r12, #80      
    add     r1, r12, #44
    stmia   r0, {r5 - r9}                          ; Save p->XcoeffsB[]    
    add     r11, r14, #44
    ldmia   r1, {r2 - r5}                          ; r2 := p->XcoeffsA[0],r3 := p->XcoeffsA[1]
                                                   ; r4 := p->XcoeffsA[2],r5 := p->XcoeffsA[3]    
    ldmia   r11!, {r6 ,r7}                          ; r6 := p->buf[XADAPTCOEFFSA-3],r7 := p->buf[XADAPTCOEFFSA-2]
    ldmia   r11, {r8 ,r9}                                               ; r8 := p->buf[XADAPTCOEFFSA-1]r9 := p->buf[XADAPTCOEFFSA]
    sub     r5, r5, r6                             ; r5 := p->XcoeffsA[3] - p->buf[XADAPTCOEFFSA-3]
    sub     r4, r4, r7                             ; r4 := p->XcoeffsA[2] - p->buf[XADAPTCOEFFSA-2]
    sub     r3, r3, r8                             ; r3 := p->XcoeffsA[1] - p->buf[XADAPTCOEFFSA-1]
    sub     r2, r2, r9                             ; r2 := p->XcoeffsA[0] - p->buf[XADAPTCOEFFSA]    
    b       second_2


first_2  ;; *decoded1 < 0

    add     r5, r5, r11                            ; r5 := p->XcoeffsB[0] + p->buf[XADAPTCOEFFSB]
    add     r6, r6, r10                            ; r6 := p->XcoeffsB[1] + p->buf[XADAPTCOEFFSB-1]
    add     r9, r9, r2                             ; r9 := p->XcoeffsB[4] + p->buf[XADAPTCOEFFSB-4]
    add     r8, r8, r3                             ; r8 := p->XcoeffsB[3] + p->buf[XADAPTCOEFFSB-3]
    add     r0, r12, #80      
    add     r7, r7, r4                             ; r7 := p->XcoeffsB[2] + p->buf[XADAPTCOEFFSB-2]
    add     r1, r12, #44
    stmia   r0, {r5 - r9}                          ; Save p->XcoeffsB[]
    add     r11, r14, #44
    ;ldmia   r1, {r2 - r5}                          ; r2 := p->XcoeffsA[0],r3 := p->XcoeffsA[1]
                                                   ; r4 := p->XcoeffsA[2],r5 := p->XcoeffsA[3]
                                                       
    ;ldmia    r1!,{r2,r3}    
    ldmia    r1,{r2-r5}
    ldmia    r11!,{r6,r7} 
    ldmia    r11,{r8,r9} 
    ;ldmia   r11, {r6 - r9}                          ; r6 := p->buf[XADAPTCOEFFSA-3],r7 := p->buf[XADAPTCOEFFSA-2]
                                                   ; r8 := p->buf[XADAPTCOEFFSA-1],r9 := p->buf[XADAPTCOEFFSA]
    add     r5, r5, r6                             ; r5 := p->XcoeffsA[3] + p->buf[XADAPTCOEFFSA-3]
    add     r4, r4, r7                             ; r4 := p->XcoeffsA[2] + p->buf[XADAPTCOEFFSA-2]
    add     r3, r3, r8                             ; r3 := p->XcoeffsA[1] + p->buf[XADAPTCOEFFSA-1]
    add     r2, r2, r9                             ; r2 := p->XcoeffsA[0] + p->buf[XADAPTCOEFFSA]
    
second_2
    stmia   r1, {r2 - r5}                          ; Save p->XcoeffsA

third_2
    
;;;;;;;;;;;;;;;;;;;;;;;;;;;; COMMON

    add     r14, r14, #4                ;; p->buf++
    add     r11, p, #100    ;; r11 := &p->historybuffer[0]
    ;sub    r10, r14, #PREDICTOR_HISTORY_SIZE*4
    sub     r10, r14, #2048
                                       ;; r10 := p->buf - PREDICTOR_HISTORY_SIZE

    ldr     r0, [sp, #8]
    cmp     r10, r11
    beq     move_hist     ;; The history buffer is full, we need to do a memmove

    ;; Check loop count
    subs    r0, r0, #1
    strne   r0, [sp, #8]
    bne     loop

done
    str     r14, [r12]              ;; Save value of p->buf
    add     sp, sp, #12             ;; Don't bother restoring r1-r3 
    ldmia   sp!, {r4 - r11, pc}

move_hist
    ;; dest = r11 (p->historybuffer)
    ;; src = r14 (p->buf)
    ;; n = 200

     ldmia   r14!,{r0,r1}
     ldmia   r14!,{r2,r3}
     stmia   r11!,{r0,r1}
     ldmia   r14!,{r4,r5}
     stmia   r11!,{r2,r3}
     ldmia   r14!,{r6,r7}
     stmia   r11!,{r4,r5}
     ldmia   r14!,{r8,r9}
     stmia   r11!,{r6,r7}
     ldmia   r14!,{r0,r1}
     stmia   r11!,{r8,r9}
     ldmia   r14!,{r2,r3}
     stmia   r11!,{r0,r1}
     ldmia   r14!,{r4,r5}
     stmia   r11!,{r2,r3}
     ldmia   r14!,{r6,r7}
     stmia   r11!,{r4,r5}
     ldmia   r14!,{r8,r9}
     stmia   r11!,{r6,r7}
     ldmia   r14!,{r0,r1}
     stmia   r11!,{r8,r9}
     ldmia   r14!,{r2,r3}
     stmia   r11!,{r0,r1}
     ldmia   r14!,{r4,r5}
     stmia   r11!,{r2,r3}
     ldmia   r14!,{r6,r7}
     stmia   r11!,{r4,r5}
     ldmia   r14!,{r8,r9}
     stmia   r11!,{r6,r7}
     ldmia   r14!,{r0,r1}
     stmia   r11!,{r8,r9}
     ldmia   r14!,{r2,r3}
     stmia   r11!,{r0,r1}
     ldmia   r14!,{r4,r5}
     stmia   r11!,{r2,r3}
     ldmia   r14!,{r6,r7}
     stmia   r11!,{r4,r5}
     ldmia   r14!,{r8,r9}
     stmia   r11!,{r6,r7}   
     ldmia   r14!,{r0,r1}
     stmia   r11!,{r8,r9}
     ldmia   r14!,{r2,r3}
     stmia   r11!,{r0,r1}
     ldmia   r14!,{r4,r5}
     stmia   r11!,{r2,r3}
     ldmia   r14!,{r6,r7}
     stmia   r11!,{r4,r5}
     ldmia   r14!,{r8,r9}
     stmia   r11!,{r6,r7}   
     ldr     r0, [sp, #8] 
     stmia   r11!,{r8,r9}                     
     
     
     
     
     
     
    ;ldmia   r14!, {r0-r9}    ;; 40 bytes
    ;stmia   r11!, {r0-r9}    ;
    ;ldmia   r14!, {r0-r9}    ;; 40 bytes
    ;stmia   r11!, {r0-r9}    ;
    ;ldmia   r14!, {r0-r9}    ;; 40 bytes
    ;stmia   r11!, {r0-r9}    ;
    ;ldmia   r14!, {r0-r9}    ;; 40 bytes
    ;stmia   r11!, {r0-r9}    ;
    ;ldmia   r14!, {r0-r9}    ;; 40 bytes
    ;stmia   r11!, {r0-r9}
    ;ldr     r0, [sp, #8]

    add     r14, r12, #100    ;; p->buf = &p->historybuffer[0]

    ;; Check loop count
    subs    r0, r0, #1
    strne   r0, [sp, #8]
    bne     loop
    
    b       done
    ;.size   predictor_decode_stereo, .-predictor_decode_stereo
    ;
    ;.global     predictor_decode_mono
    ;.type       predictor_decode_mono,%function

;; Register usage:
;;
;; r0-r11 - scratch
;; r12 - struct predictor_t* p
;; r14 - int32_t* p->buf

;; void predictor_decode_mono(struct predictor_t* p,
;;                            int32_t* decoded0,
;;                            int count)
	AREA |s2.text|, CODE, READONLY      
    EXPORT predictor_decode_mono_asm   


predictor_decode_mono_asm
    stmdb   sp!, {r1, r2, r4-r11, lr}

    ;; r1 (decoded0) is [sp]
    ;; r2 (count)    is [sp, #4]

    mov     r12, r0        ;; r12 := p
    ldr     r14, [r0]      ;; r14 := p->buf
    
loopm

;;;;;;;;;;;;;;;;;;;;;;;;;;;; PREDICTOR

    ldr     r11, [r12, #4]    ;; r11 := p->YlastA
                                   ;
    add     r2, r14, #188   ;; r2 := &p->buf[YDELAYA-3]
    ldmia   r2, {r2, r3, r10}      ;; r2 := p->buf[YDELAYA-3]
                                   ;; r3 := p->buf[YDELAYA-2]
                                   ;; r10 := p->buf[YDELAYA-1]
                                   ;
    add     r5, r12, #28     ;; r5 := &p->YcoeffsA[0]
    ldmia   r5, {r6 - r9}          ;; r6 := p->YcoeffsA[0]
                                   ;; r7 := p->YcoeffsA[1]
                                   ;; r8 := p->YcoeffsA[2]
                                   ;; r9 := p->YcoeffsA[3]
                                   ;
    subs    r10, r11, r10          ;; r10 := r11 - r10

    strd r10, r11, [r14, #196]
                                   ;; p->buf[YDELAYA-1] = r10
                                   ;; p->buf[YDELAYA] = r11
                                   ;
    mul     r0, r11, r6            ;; r0 := p->buf[YDELAYA] * p->YcoeffsA[0]
    mla     r0, r10, r7, r0        ;; r0 += p->buf[YDELAYA-1] * p->YcoeffsA[1]
    mla     r0, r3, r8, r0         ;; r0 += p->buf[YDELAYA-2] * p->YcoeffsA[2]
    mla     r0, r2, r9, r0         ;; r0 += p->buf[YDELAYA-3] * p->YcoeffsA[3]

    ;; flags were set above, in the subs instruction
    mvngt   r10, #0
    movlt   r10, #1                 ;; r10 := SIGN(r10) (see .c for SIGN macro)

    cmp     r11, #0
    mvngt   r11, #0
    movlt   r11, #1                 ;; r11 := SIGN(r11) (see .c for SIGN macro)

    strd r10, r11, [r14, #68]
                                    ;; p->buf[YADAPTCOEFFSA-1] := r10
                                    ;; p->buf[YADAPTCOEFFSA] := r11

    ldr     r2, [sp]                ;; r2 := decoded0
    ldr     r4, [r12, #24]    ;; r4 := p->YfilterA
    ldr     r3, [r2]                ;; r3 := *decoded0
    rsb     r4, r4, r4, lsl #5      ;; r4 := r4 * 32 - r4 ( == r4*31)
    add     r1, r3, r0, asr #10     ;; r1 := r3 + (r0 >> 10)
    str     r1, [r12, #4]      ;; p->YlastA := r1
    add     r1, r1, r4, asr #5      ;; r1 := r1 + (r4 >> 5)
    str     r1, [r12, #24]    ;; p->YfilterA := r1

    ; r1 contains p->YfilterA
    ; r2 contains decoded0
    ; r3 contains *decoded0

    ; r6, r7, r8, r9 contain p->YcoeffsA[0..3]
    ; r10, r11 contain p->buf[YADAPTCOEFFSA-1] and p->buf[YADAPTCOEFFSA]

    str     r1, [r2], #4            ; *(decoded0++) := r1  (p->YfilterA)
    str     r2, [sp]                ; save decoded0
    cmp     r3, #0
    beq     third_3

    ldrd r2, r3, [r14, #60]
                                    ; r2 := p->buf[YADAPTCOEFFSA-3]
                                    ; r3 := p->buf[YADAPTCOEFFSA-2]
    blt     first_3

    ; *decoded0 > 0

    sub     r6, r6, r11     ; r6 := p->YcoeffsA[0] - p->buf[YADAPTCOEFFSA]
    sub     r7, r7, r10     ; r7 := p->YcoeffsA[1] - p->buf[YADAPTCOEFFSA-1]
    sub     r9, r9, r2      ; r9 := p->YcoeffsA[3] - p->buf[YADAPTCOEFFSA-3]
    sub     r8, r8, r3      ; r8 := p->YcoeffsA[2] - p->buf[YADAPTCOEFFSA-2]

    b       second_3

first_3  ; *decoded0 < 0

    add     r6, r6, r11     ; r6 := p->YcoeffsA[0] + p->buf[YADAPTCOEFFSA]
    add     r7, r7, r10     ; r7 := p->YcoeffsA[1] + p->buf[YADAPTCOEFFSA-1]
    add     r9, r9, r2      ; r9 := p->YcoeffsA[3] + p->buf[YADAPTCOEFFSA-3]
    add     r8, r8, r3      ; r8 := p->YcoeffsA[2] + p->buf[YADAPTCOEFFSA-2]
    
second_3
    stmia   r5, {r6 - r9}           ; Save p->YcoeffsA

third_3

;;;;;;;;;;;;;;;;;;;;;;;;;;; COMMON

    add     r14, r14, #4                ; p->buf++

    add     r11, r12, #100    ; r11 := &p->historybuffer[0]
  
    ;sub     r10, r14, #PREDICTOR_HISTORY_SIZE*4
    sub     r10, r14, #2048
                                       ; r10 := p->buf - PREDICTOR_HISTORY_SIZE

    ldr     r0, [sp, #4]
    cmp     r10, r11
    beq     move_histm    ; The history buffer is full, we need to do a memmove

    ; Check loop count
    subs    r0, r0, #1
    strne   r0, [sp, #4]
    bne     loopm

donem
    str     r14, [r12]              ; Save value of p->buf
    add     sp, sp, #8              ; Don't bother restoring r1, r2
    ldmia   sp!, {r4 - r11, pc}

move_histm
    ; dest = r11 (p->historybuffer)
    ; src = r14 (p->buf)
    ; n = 200

    ldmia   r14!, {r0-r9}    ; 40 bytes
    stmia   r11!, {r0-r9}
    ldmia   r14!, {r0-r9}    ; 40 bytes
    stmia   r11!, {r0-r9}
    ldmia   r14!, {r0-r9}    ; 40 bytes
    stmia   r11!, {r0-r9}
    ldmia   r14!, {r0-r9}    ; 40 bytes
    stmia   r11!, {r0-r9}
    ldmia   r14!, {r0-r9}    ; 40 bytes
    stmia   r11!, {r0-r9}

    ldr     r0, [sp, #4]
    add     r14, r12, #100    ; p->buf = &p->historybuffer[0]

    ; Check loop count
    subs    r0, r0, #1
    strne   r0, [sp, #4]
    bne     loopm
    
    b       donem
    END
   ; .size   predictor_decode_mono, .-predictor_decode_mono

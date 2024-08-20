package backend.parallel;

import backend.riscv.RiscvFunction;
import backend.riscv.RiscvModule;

/**
 * 参考往届优秀开源作品CMMC实现的运行时库
 *
 * @see <a href="https://gitlab.eduxiji.net/educg-group-17291-1894922/202314325201374-1031/-/blob/riscv_fix/src/RISCV/SysYRuntime.hpp">CMMC优秀作品</a>
 */
public class ExternLib {

    public static boolean need(RiscvModule riscvModule) {
        boolean need = false;
        for (RiscvFunction function : riscvModule.funcList) {
            if (function.isExternal && (function.name.equals("NELCacheLookup") || function.name.equals("NELParallelFor"))) {
                need = true;
                break;
            }
        }
        return need;
    }

    public static final String model = """
            .file\t"NEL_sysy_rt.cpp"
            \t.option pic
            \t.attribute arch, "rv64i2p1_m2p0_a2p1_f2p2_d2p2_c2p0_zicsr2p0_zifencei2p0_zba1p0_zbb1p0"
            \t.attribute unaligned_access, 0
            \t.attribute stack_align, 16
            \t.text
            \t.align\t1
            \t.type\t_ZN12_GLOBAL__N_110NELWorkerEPv, @function
            _ZN12_GLOBAL__N_110NELWorkerEPv:
            .LFB1259:
            \t.cfi_startproc
            \taddi\tsp,sp,-192
            \t.cfi_def_cfa_offset 192
            \tsd\ts0,176(sp)
            \t.cfi_offset 8, -16
            \tmv\ts0,a0
            \tsd\tra,184(sp)
            \tsd\ts1,168(sp)
            \tsd\ts2,160(sp)
            \tsd\ts3,152(sp)
            \tsd\ts4,144(sp)
            \tsd\ts5,136(sp)
            \t.cfi_offset 1, -8
            \t.cfi_offset 9, -24
            \t.cfi_offset 18, -32
            \t.cfi_offset 19, -40
            \t.cfi_offset 20, -48
            \t.cfi_offset 21, -56
            \tfence\tiorw,iorw
            \tlw\ta5,16(a0)
            \tfence\tiorw,iorw
            \tli\ta4,1023
            \tsext.w\ta2,a5
            \tmv\ts1,sp
            \tzext.w\ta5,a5
            \tbgtu\ta5,a4,.L2
            \tli\ta3,1
            \tsrli\ta5,a5,6
            \tsll\ta3,a3,a2
            \tsh3add\ta5,a5,s1
            \tld\ta4,0(a5)
            \tor\ta4,a4,a3
            \tsd\ta4,0(a5)
            .L2:
            \tli\ta0,178
            \taddi\ts2,s0,20
            \tcall\tsyscall@plt
            \taddi\ts3,s0,40
            \tmv\ta2,s1
            \tsext.w\ta0,a0
            \tli\ta1,128
            \taddi\ts1,s0,44
            \tcall\tsched_setaffinity@plt
            \tli\ts4,1
            \tj\t.L6
            .L21:
            \tfence iorw,ow;  1: lr.w.aq a5,0(s3); bne a5,s4,1f; sc.w.aq a4,zero,0(s3); bnez a4,1b; 1:
            \taddiw\ta5,a5,-1
            \tli\ts5,1
            \tbne\ta5,zero,.L4
            .L7:
            \tfence\tiorw,iorw
            \tlw\ta5,0(s2)
            \tfence\tiorw,iorw
            \tbeq\ta5,zero,.L5
            \tfence\tiorw,iorw
            \tfence\tiorw,iorw
            \tld\ta5,24(s0)
            \tfence\tiorw,iorw
            \tfence\tiorw,iorw
            \tlw\ta0,32(s0)
            \tfence\tiorw,iorw
            \tfence\tiorw,iorw
            \tsext.w\ta0,a0
            \tlw\ta1,36(s0)
            \tfence\tiorw,iorw
            \tjalr\ta5
            \tfence\tiorw,iorw
            \tfence iorw,ow;  1: lr.w.aq a5,0(s1); bne a5,zero,1f; sc.w.aq a4,s4,0(s1); bnez a4,1b; 1:
            \tsext.w\ta5,a5
            \tbne\ta5,zero,.L6
            \tmv\ta1,s1
            \tli\ta6,0
            \tli\ta4,0
            \tli\ta3,1
            \tli\ta2,1
            \tli\ta0,98
            \tcall\tsyscall@plt
            .L6:
            \tfence\tiorw,iorw
            \tlw\ta5,0(s2)
            \tfence\tiorw,iorw
            \tbne\ta5,zero,.L21
            .L5:
            \tld\tra,184(sp)
            \t.cfi_remember_state
            \t.cfi_restore 1
            \tli\ta0,0
            \tld\ts0,176(sp)
            \t.cfi_restore 8
            \tld\ts1,168(sp)
            \t.cfi_restore 9
            \tld\ts2,160(sp)
            \t.cfi_restore 18
            \tld\ts3,152(sp)
            \t.cfi_restore 19
            \tld\ts4,144(sp)
            \t.cfi_restore 20
            \tld\ts5,136(sp)
            \t.cfi_restore 21
            \taddi\tsp,sp,192
            \t.cfi_def_cfa_offset 0
            \tjr\tra
            .L4:
            \t.cfi_restore_state
            \tmv\ta1,s3
            \tli\ta6,0
            \tli\ta5,0
            \tli\ta4,0
            \tli\ta3,0
            \tli\ta2,0
            \tli\ta0,98
            \tcall\tsyscall@plt
            \tfence iorw,ow;  1: lr.w.aq a5,0(s3); bne a5,s5,1f; sc.w.aq a4,zero,0(s3); bnez a4,1b; 1:
            \taddiw\ta5,a5,-1
            \tbeq\ta5,zero,.L7
            \tj\t.L4
            \t.cfi_endproc
            .LFE1259:
            \t.size\t_ZN12_GLOBAL__N_110NELWorkerEPv, .-_ZN12_GLOBAL__N_110NELWorkerEPv
            \t.section\t.text.startup,"ax",@progbits
            \t.align\t1
            \t.globl\tNELInitRuntime
            \t.type\tNELInitRuntime, @function
            NELInitRuntime:
            .LFB1260:
            \t.cfi_startproc
            \taddi\tsp,sp,-64
            \t.cfi_def_cfa_offset 64
            \tsd\ts2,32(sp)
            \t.cfi_offset 18, -32
            \tli\ts2,331776
            \tsd\ts3,24(sp)
            \t.cfi_offset 19, -40
            \tli\ts3,131072
            \taddi\ts3,s3,34
            \taddi\ts2,s2,-256
            \tsd\ts0,48(sp)
            \t.cfi_offset 8, -16
            \tlla\ts0,.LANCHOR0
            \tsd\ts1,40(sp)
            \t.cfi_offset 9, -24
            \tli\ts1,0
            \tsd\ts4,16(sp)
            \t.cfi_offset 20, -48
            \tli\ts4,4
            \tsd\ts5,8(sp)
            \t.cfi_offset 21, -56
            \tlla\ts5,_ZN12_GLOBAL__N_110NELWorkerEPv
            \tsd\ts6,0(sp)
            \t.cfi_offset 22, -64
            \tli\ts6,1
            \tsd\tra,56(sp)
            \t.cfi_offset 1, -8
            .L23:
            \taddi\ta5,s0,20
            \tfence iorw,ow; amoswap.w.aq zero,s6,0(a5)
            \tmv\ta3,s3
            \tli\ta5,0
            \tli\ta4,-1
            \tli\ta2,3
            \tli\ta1,1048576
            \tli\ta0,0
            \tcall\tmmap@plt
            \tsd\ta0,8(s0)
            \taddi\ta5,s0,16
            \tfence iorw,ow; amoswap.w.aq zero,s1,0(a5)
            \tli\ta5,1048576
            \tmv\ta3,s0
            \tld\ta1,8(s0)
            \tmv\ta2,s2
            \tadd\ta1,a1,a5
            \tmv\ta0,s5
            \taddi\ts0,s0,48
            \taddiw\ts1,s1,1
            \tcall\tclone@plt
            \tsw\ta0,-48(s0)
            \tbne\ts1,s4,.L23
            \tld\tra,56(sp)
            \t.cfi_restore 1
            \tld\ts0,48(sp)
            \t.cfi_restore 8
            \tld\ts1,40(sp)
            \t.cfi_restore 9
            \tld\ts2,32(sp)
            \t.cfi_restore 18
            \tld\ts3,24(sp)
            \t.cfi_restore 19
            \tld\ts4,16(sp)
            \t.cfi_restore 20
            \tld\ts5,8(sp)
            \t.cfi_restore 21
            \tld\ts6,0(sp)
            \t.cfi_restore 22
            \taddi\tsp,sp,64
            \t.cfi_def_cfa_offset 0
            \tjr\tra
            \t.cfi_endproc
            .LFE1260:
            \t.size\tNELInitRuntime, .-NELInitRuntime
            \t.section\t.init_array,"aw"
            \t.align\t3
            \t.dword\tNELInitRuntime
            \t.section\t.text.exit,"ax",@progbits
            \t.align\t1
            \t.globl\tNELUninitRuntime
            \t.type\tNELUninitRuntime, @function
            NELUninitRuntime:
            .LFB1261:
            \t.cfi_startproc
            \taddi\tsp,sp,-32
            \t.cfi_def_cfa_offset 32
            \tsd\ts0,16(sp)
            \t.cfi_offset 8, -16
            \tlla\ts0,.LANCHOR0+40
            \tsd\ts1,8(sp)
            \t.cfi_offset 9, -24
            \tli\ts1,1
            \tsd\ts2,0(sp)
            \t.cfi_offset 18, -32
            \tlla\ts2,.LANCHOR0+232
            \tsd\tra,24(sp)
            \t.cfi_offset 1, -8
            .L30:
            \taddi\ta5,s0,-20
            \tfence iorw,ow; amoswap.w.aq zero,zero,0(a5)
            \tfence iorw,ow;  1: lr.w.aq a7,0(s0); bne a7,zero,1f; sc.w.aq a5,s1,0(s0); bnez a5,1b; 1:
            \tsext.w\ta7,a7
            \tmv\ta1,s0
            \tli\ta6,0
            \tli\ta5,0
            \tli\ta4,0
            \tli\ta3,1
            \tli\ta2,1
            \tli\ta0,98
            \tbne\ta7,zero,.L27
            \taddi\ts0,s0,48
            \tcall\tsyscall@plt
            \tli\ta2,0
            \tli\ta1,0
            \tlw\ta0,-88(s0)
            \tcall\twaitpid@plt
            \tbne\ts2,s0,.L30
            .L26:
            \tld\tra,24(sp)
            \t.cfi_remember_state
            \t.cfi_restore 1
            \tld\ts0,16(sp)
            \t.cfi_restore 8
            \tld\ts1,8(sp)
            \t.cfi_restore 9
            \tld\ts2,0(sp)
            \t.cfi_restore 18
            \taddi\tsp,sp,32
            \t.cfi_def_cfa_offset 0
            \tjr\tra
            .L27:
            \t.cfi_restore_state
            \tlw\ta0,-40(s0)
            \tli\ta2,0
            \taddi\ts0,s0,48
            \tli\ta1,0
            \tcall\twaitpid@plt
            \tbne\ts0,s2,.L30
            \tj\t.L26
            \t.cfi_endproc
            .LFE1261:
            \t.size\tNELUninitRuntime, .-NELUninitRuntime
            \t.section\t.fini_array,"aw"
            \t.align\t3
            \t.dword\tNELUninitRuntime
            \t.text
            \t.align\t1
            \t.globl\tNELParallelFor
            \t.type\tNELParallelFor, @function
            NELParallelFor:
            .LFB1265:
            \t.cfi_startproc
            \tbge\ta0,a1,.L116
            \taddi\tsp,sp,-144
            \t.cfi_def_cfa_offset 144
            \tsubw\tt4,a1,a0
            \tli\ta5,15
            \tsd\ts2,112(sp)
            \t.cfi_offset 18, -32
            \tmv\ts2,a1
            \tsd\ts3,104(sp)
            \t.cfi_offset 19, -40
            \tmv\ts3,a2
            \tsd\ts5,88(sp)
            \t.cfi_offset 21, -56
            \tmv\ts5,t4
            \tsd\ts11,40(sp)
            \t.cfi_offset 27, -104
            \tmv\ts11,a0
            \tsd\tra,136(sp)
            \tsd\ts0,128(sp)
            \tsd\ts1,120(sp)
            \tsd\ts4,96(sp)
            \tsd\ts6,80(sp)
            \tsd\ts7,72(sp)
            \tsd\ts8,64(sp)
            \tsd\ts9,56(sp)
            \tsd\ts10,48(sp)
            \t.cfi_offset 1, -8
            \t.cfi_offset 8, -16
            \t.cfi_offset 9, -24
            \t.cfi_offset 20, -48
            \t.cfi_offset 22, -64
            \t.cfi_offset 23, -72
            \t.cfi_offset 24, -80
            \t.cfi_offset 25, -88
            \t.cfi_offset 26, -96
            \tbgt\tt4,a5,.L34
            \tld\tra,136(sp)
            \t.cfi_remember_state
            \t.cfi_restore 1
            \tld\ts0,128(sp)
            \t.cfi_restore 8
            \tld\ts1,120(sp)
            \t.cfi_restore 9
            \tld\ts2,112(sp)
            \t.cfi_restore 18
            \tld\ts3,104(sp)
            \t.cfi_restore 19
            \tld\ts4,96(sp)
            \t.cfi_restore 20
            \tld\ts5,88(sp)
            \t.cfi_restore 21
            \tld\ts6,80(sp)
            \t.cfi_restore 22
            \tld\ts7,72(sp)
            \t.cfi_restore 23
            \tld\ts8,64(sp)
            \t.cfi_restore 24
            \tld\ts9,56(sp)
            \t.cfi_restore 25
            \tld\ts10,48(sp)
            \t.cfi_restore 26
            \tld\ts11,40(sp)
            \t.cfi_restore 27
            \taddi\tsp,sp,144
            \t.cfi_def_cfa_offset 0
            \tjr\ta2
            .L34:
            \t.cfi_restore_state
            \tlla\ta7,.LANCHOR0
            \tli\ta3,16
            \tlw\ta4,1088(a7)
            \tli\ta1,0
            \tlla\ta2,.LANCHOR0+192
            \tli\tt3,16
            .L39:
            \tbeq\ta4,t3,.L80
            \tzext.w\ta6,a4
            \tslli.uw\ta5,a4,3
            \tsub\ta5,a5,a6
            \tsh3add\ts0,a5,a2
            .L35:
            \tslli\ta5,a6,3
            \taddiw\ta3,a3,-1
            \tsub\ta5,a5,a6
            \tsh3add\ta5,a5,a7
            \tlbu\tt1,204(a5)
            \tbeq\tt1,zero,.L36
            \tld\tt1,192(a5)
            \tbeq\tt1,s3,.L119
            .L36:
            \taddiw\ta4,a4,1
            \tli\ta1,1
            \tbne\ta3,zero,.L39
            \tlbu\ta5,204(a7)
            \tbeq\ta5,zero,.L40
            \tlbu\ta5,260(a7)
            \tbeq\ta5,zero,.L81
            \tlbu\ta5,316(a7)
            \tbeq\ta5,zero,.L82
            \tlbu\ta5,372(a7)
            \tbeq\ta5,zero,.L83
            \tlbu\ta5,428(a7)
            \tbeq\ta5,zero,.L84
            \tlbu\ta5,484(a7)
            \tbeq\ta5,zero,.L85
            \tlbu\ta5,540(a7)
            \tbeq\ta5,zero,.L86
            \tlbu\ta5,596(a7)
            \tbeq\ta5,zero,.L87
            \tlbu\ta5,652(a7)
            \tbeq\ta5,zero,.L88
            \tlbu\ta5,708(a7)
            \tbeq\ta5,zero,.L89
            \tlbu\ta5,764(a7)
            \tbeq\ta5,zero,.L90
            \tlbu\ta5,820(a7)
            \tbeq\ta5,zero,.L91
            \tlbu\ta5,876(a7)
            \tbeq\ta5,zero,.L92
            \tlbu\ta5,932(a7)
            \tbeq\ta5,zero,.L93
            \tlbu\ta5,988(a7)
            \tbeq\ta5,zero,.L94
            \tlbu\ta5,1044(a7)
            \tli\ta3,15
            \tbeq\ta5,zero,.L40
            \tlw\ta1,264(a7)
            \tlw\ta5,208(a7)
            \tlw\ta6,320(a7)
            \tsgtu\ta3,a5,a1
            \tlw\tt1,376(a7)
            \tbleu a5,a1,1f; mv a5,a1; 1: # movcc
            \tmv\ta0,a5
            \tbgeu a6,a5,1f; mv a0,a6; 1: # movcc
            \tlw\ta4,432(a7)
            \tbgeu a6,a5,1f; li a3,2; 1: # movcc
            \tmv\ta5,a0
            \tbgeu t1,a0,1f; mv a5,t1; 1: # movcc
            \tlw\ta1,488(a7)
            \tbgeu t1,a0,1f; li a3,3; 1: # movcc
            \tmv\ta0,a5
            \tbgeu a4,a5,1f; mv a0,a4; 1: # movcc
            \tlw\ta6,544(a7)
            \tbgeu a4,a5,1f; li a3,4; 1: # movcc
            \tmv\ta5,a0
            \tbleu a0,a1,1f; mv a5,a1; 1: # movcc
            \tlw\tt1,600(a7)
            \tbleu a0,a1,1f; li a3,5; 1: # movcc
            \tmv\ta0,a5
            \tbgeu a6,a5,1f; mv a0,a6; 1: # movcc
            \tlw\ta4,656(a7)
            \tbgeu a6,a5,1f; li a3,6; 1: # movcc
            \tmv\ta6,a0
            \tbgeu t1,a0,1f; mv a6,t1; 1: # movcc
            \tlw\ta1,712(a7)
            \tbgeu t1,a0,1f; li a3,7; 1: # movcc
            \tmv\tt1,a6
            \tbleu a6,a4,1f; mv t1,a4; 1: # movcc
            \tlw\ta5,768(a7)
            \tbleu a6,a4,1f; li a3,8; 1: # movcc
            \tmv\ta6,t1
            \tbleu t1,a1,1f; mv a6,a1; 1: # movcc
            \tlw\ta0,824(a7)
            \tbleu t1,a1,1f; li a3,9; 1: # movcc
            \tmv\tt1,a6
            \tbleu a6,a5,1f; mv t1,a5; 1: # movcc
            \tlw\ta4,880(a7)
            \tbleu a6,a5,1f; li a3,10; 1: # movcc
            \tmv\ta6,t1
            \tbleu t1,a0,1f; mv a6,a0; 1: # movcc
            \tlw\ta1,936(a7)
            \tbleu t1,a0,1f; li a3,11; 1: # movcc
            \tmv\ta0,a6
            \tbleu a6,a4,1f; mv a0,a4; 1: # movcc
            \tlw\ta5,992(a7)
            \tbleu a6,a4,1f; li a3,12; 1: # movcc
            \tmv\ta4,a0
            \tbleu a0,a1,1f; mv a4,a1; 1: # movcc
            \tlw\tt1,1048(a7)
            \tbleu a0,a1,1f; li a3,13; 1: # movcc
            \tmv\ta1,a4
            \tbleu a4,a5,1f; mv a1,a5; 1: # movcc
            \tbleu a4,a5,1f; li a3,14; 1: # movcc
            \tbgeu t1,a1,1f; li a3,15; 1: # movcc
            \tslli\ta5,a3,3
            \tsub\ta5,a5,a3
            \tsh3add\ta4,a5,a7
            \tsh3add\ts0,a5,a2
            \tli\ta5,1
            \tsw\ta3,1088(a7)
            \tsd\ts3,192(a4)
            \tsw\ts5,200(a4)
            \tsw\ta5,208(a4)
            .L38:
            \tlw\ts6,16(s0)
            \tli\ta5,99
            \tbleu\ts6,a5,.L57
            \tli\ta5,159
            \tbleu\ts6,a5,.L120
            \tlw\ts6,48(s0)
            \tbeq\ts6,zero,.L60
            \tli\tt3,1
            \tsllw\ts1,t3,s6
            \tmv\ts8,s1
            .L61:
            \tsd\tzero,0(sp)
            .L59:
            \tli\ta5,1
            \tbne\ts8,a5,.L64
            \tmv\ta1,s2
            \tmv\ta0,s11
            \tjalr\ts3
            \tld\ta5,0(sp)
            \tbne\ta5,zero,.L121
            .L32:
            \tld\tra,136(sp)
            \t.cfi_remember_state
            \t.cfi_restore 1
            \tld\ts0,128(sp)
            \t.cfi_restore 8
            \tld\ts1,120(sp)
            \t.cfi_restore 9
            \tld\ts2,112(sp)
            \t.cfi_restore 18
            \tld\ts3,104(sp)
            \t.cfi_restore 19
            \tld\ts4,96(sp)
            \t.cfi_restore 20
            \tld\ts5,88(sp)
            \t.cfi_restore 21
            \tld\ts6,80(sp)
            \t.cfi_restore 22
            \tld\ts7,72(sp)
            \t.cfi_restore 23
            \tld\ts8,64(sp)
            \t.cfi_restore 24
            \tld\ts9,56(sp)
            \t.cfi_restore 25
            \tld\ts10,48(sp)
            \t.cfi_restore 26
            \tld\ts11,40(sp)
            \t.cfi_restore 27
            \taddi\tsp,sp,144
            \t.cfi_def_cfa_offset 0
            \tjr\tra
            .L80:
            \t.cfi_restore_state
            \tmv\ts0,a2
            \tli\ta6,0
            \tli\ta1,1
            \tli\ta4,0
            \tj\t.L35
            .L119:
            \tlw\ta5,200(a5)
            \tbne\ta5,t4,.L36
            \tbeq\ta1,zero,.L37
            \tsw\ta4,1088(a7)
            .L37:
            \tslli\ta5,a6,3
            \tsub\ta5,a5,a6
            \tsh3add\ta5,a5,a7
            \tlw\ta4,208(a5)
            \taddiw\ta4,a4,1
            \tsw\ta4,208(a5)
            \tj\t.L38
            .L116:
            \t.cfi_def_cfa_offset 0
            \t.cfi_restore 1
            \t.cfi_restore 8
            \t.cfi_restore 9
            \t.cfi_restore 18
            \t.cfi_restore 19
            \t.cfi_restore 20
            \t.cfi_restore 21
            \t.cfi_restore 22
            \t.cfi_restore 23
            \t.cfi_restore 24
            \t.cfi_restore 25
            \t.cfi_restore 26
            \t.cfi_restore 27
            \tret
            .L60:
            \t.cfi_def_cfa_offset 144
            \t.cfi_offset 1, -8
            \t.cfi_offset 8, -16
            \t.cfi_offset 9, -24
            \t.cfi_offset 18, -32
            \t.cfi_offset 19, -40
            \t.cfi_offset 20, -48
            \t.cfi_offset 21, -56
            \t.cfi_offset 22, -64
            \t.cfi_offset 23, -72
            \t.cfi_offset 24, -80
            \t.cfi_offset 25, -88
            \t.cfi_offset 26, -96
            \t.cfi_offset 27, -104
            \tld\ta5,24(s0)
            \tli\ts8,1
            \tld\ta4,32(s0)
            \tli\ts1,1
            \tbge\ta4,a5,.L62
            \tmv\ta5,a4
            \tli\ts8,2
            \tli\ts1,2
            \tli\ts6,1
            .L62:
            \tld\ta4,40(s0)
            \tble\ta5,a4,.L63
            \tli\ta5,2
            \tsw\ta5,48(s0)
            .L57:
            \tfence\tiorw,iorw
            \tsrliw\ts5,s5,2
            \tli\ts6,2
            \taddiw\ts5,s5,3
            \tli\ts8,4
            \tandi\ts5,s5,-4
            \tli\ts1,4
            \tsext.w\ts5,s5
            \tsd\tzero,0(sp)
            \tsw\tzero,16(sp)
            .L79:
            \taddiw\ts7,s8,-1
            \tsext.w\ts11,s11
            \taddi\ts9,sp,16
            \tlla\ts4,.LANCHOR0+40
            \tli\ts10,0
            .L72:
            \tsext.w\ta4,s11
            \taddw\ts11,s5,s11
            \tmin\ta5,s11,s2
            \tbne s7,s10,1f; mv a5,s2; 1: # movcc
            \tble\ta5,a4,.L70
            \taddi\ta3,s4,-16
            \tfence iorw,ow; amoswap.d.aq zero,s3,0(a3)
            \taddi\ta3,s4,-8
            \tfence iorw,ow; amoswap.w.aq zero,a4,0(a3)
            \taddi\ta4,s4,-4
            \tfence iorw,ow; amoswap.w.aq zero,a5,0(a4)
            \tli\ta4,1
            \tfence iorw,ow;  1: lr.w.aq t5,0(s4); bne t5,zero,1f; sc.w.aq a5,a4,0(s4); bnez a5,1b; 1:
            \tsext.w\tt5,t5
            \tmv\ta1,s4
            \tli\ta6,0
            \tli\ta5,0
            \tli\ta4,0
            \tli\ta3,1
            \tli\ta2,1
            \tli\ta0,98
            \tbne\tt5,zero,.L71
            \tcall\tsyscall@plt
            .L71:
            \tli\ta5,1
            \tsb\ta5,0(s9)
            .L70:
            \taddiw\ts10,s10,1
            \taddi\ts9,s9,1
            \taddi\ts4,s4,48
            \tbne\ts1,s10,.L72
            .L73:
            \tbeq\ts8,zero,.L68
            \tlla\ts3,.LANCHOR0+44
            \tli\ts4,1
            \taddi\ts2,sp,16
            \tadd.uw\ts8,s8,s2
            .L75:
            \tlbu\ta5,0(s2)
            \tbne\ta5,zero,.L74
            .L76:
            \taddi\ts2,s2,1
            \taddi\ts3,s3,48
            \tbne\ts2,s8,.L75
            .L68:
            \tfence\tiorw,iorw
            \tld\ta5,0(sp)
            \tbeq\ta5,zero,.L32
            .L121:
            \taddi\ta1,sp,16
            \tli\ta0,1
            \tsh3add.uw\ts6,s6,s0
            \tcall\tclock_gettime@plt
            \tld\ta5,16(sp)
            \tli\ta4,1000001536
            \taddi\ta4,a4,-1536
            \tld\ta3,24(sp)
            \tmul\ta5,a5,a4
            \tld\ta4,24(s6)
            \tadd\ta5,a5,a3
            \tld\ta3,8(sp)
            \tsub\ta5,a5,a3
            \tadd\ta5,a4,a5
            \tsd\ta5,24(s6)
            \tj\t.L32
            .L74:
            \tfence iorw,ow;  1: lr.w.aq a5,0(s3); bne a5,s4,1f; sc.w.aq a4,zero,0(s3); bnez a4,1b; 1:
            \taddiw\ta5,a5,-1
            \tbeq\ta5,zero,.L76
            \tli\ts5,1
            .L77:
            \tmv\ta1,s3
            \tli\ta6,0
            \tli\ta5,0
            \tli\ta4,0
            \tli\ta3,0
            \tli\ta2,0
            \tli\ta0,98
            \tcall\tsyscall@plt
            \tfence iorw,ow;  1: lr.w.aq a5,0(s3); bne a5,s5,1f; sc.w.aq a4,zero,0(s3); bnez a4,1b; 1:
            \taddiw\ta5,a5,-1
            \tbeq\ta5,zero,.L76
            \tj\t.L77
            .L81:
            \tli\ta3,1
            .L40:
            \tli\ta4,1
            \tzext.w\ta1,a3
            \tsw\ta3,1088(a7)
            \tslli.uw\ta5,a3,3
            \tsub\ta5,a5,a1
            \tsh3add\ta7,a5,a7
            \tsh3add\ts0,a5,a2
            \tsb\ta4,204(a7)
            \tsd\ts3,192(a7)
            \tsw\ts5,200(a7)
            \tsw\ta4,208(a7)
            \tj\t.L38
            .L120:
            \taddiw\ts6,s6,-100
            \tli\ta5,20
            \taddi\ta1,sp,16
            \tli\ta0,1
            \tdivuw\ts6,s6,a5
            \tli\ta5,1
            \tsd\ta5,0(sp)
            \tcall\tclock_gettime@plt
            \tli\ta5,1000001536
            \tli\tt3,1
            \tld\ts1,16(sp)
            \taddi\ta5,a5,-1536
            \tmul\ts1,s1,a5
            \tld\ta5,24(sp)
            \tadd\ta5,s1,a5
            \tsd\ta5,8(sp)
            \tsllw\ts1,t3,s6
            \tmv\ts8,s1
            \tj\t.L59
            .L64:
            \tfence\tiorw,iorw
            \tsw\tzero,16(sp)
            \tble\ts1,zero,.L73
            \tsrlw\ts5,s5,s6
            \taddiw\ts5,s5,3
            \tandi\ts5,s5,-4
            \tsext.w\ts5,s5
            \tj\t.L79
            .L63:
            \tsw\ts6,48(s0)
            \tj\t.L61
            .L85:
            \tli\ta3,5
            \tj\t.L40
            .L82:
            \tli\ta3,2
            \tj\t.L40
            .L83:
            \tli\ta3,3
            \tj\t.L40
            .L84:
            \tli\ta3,4
            \tj\t.L40
            .L86:
            \tli\ta3,6
            \tj\t.L40
            .L87:
            \tli\ta3,7
            \tj\t.L40
            .L88:
            \tli\ta3,8
            \tj\t.L40
            .L89:
            \tli\ta3,9
            \tj\t.L40
            .L90:
            \tli\ta3,10
            \tj\t.L40
            .L91:
            \tli\ta3,11
            \tj\t.L40
            .L92:
            \tli\ta3,12
            \tj\t.L40
            .L93:
            \tli\ta3,13
            \tj\t.L40
            .L94:
            \tli\ta3,14
            \tj\t.L40
            \t.cfi_endproc
            .LFE1265:
            \t.size\tNELParallelFor, .-NELParallelFor
            \t.align\t1
            \t.globl\tNELCacheLookup
            \t.type\tNELCacheLookup, @function
            NELCacheLookup:
            .LFB1267:
            \t.cfi_startproc
            \tslli\ta1,a1,32
            \tli\ta5,1021
            \tor\ta2,a1,a2
            \tremu\ta5,a2,a5
            \tslli\ta5,a5,4
            \tadd\ta0,a0,a5
            \tlw\ta5,12(a0)
            \tbeq\ta5,zero,.L125
            \tld\ta5,0(a0)
            \tbeq\ta5,a2,.L122
            \tsw\tzero,12(a0)
            .L125:
            \tsd\ta2,0(a0)
            .L122:
            \tret
            \t.cfi_endproc
            .LFE1267:
            \t.size\tNELCacheLookup, .-NELCacheLookup
            \t.align\t1
            \t.globl\tNELAddRec3SRem
            \t.type\tNELAddRec3SRem, @function
            NELAddRec3SRem:
            .LFB1268:
            \t.cfi_startproc
            \taddi\ta5,a0,-1
            \tmul\ta5,a5,a0
            \tsrli\ta0,a5,63
            \tadd\ta0,a0,a5
            \tsrai\ta0,a0,1
            \trem\ta0,a0,a1
            \tsext.w\ta0,a0
            \tret
            \t.cfi_endproc
            .LFE1268:
            \t.size\tNELAddRec3SRem, .-NELAddRec3SRem
            \t.align\t1
            \t.globl\tNELReduceAddI32
            \t.type\tNELReduceAddI32, @function
            NELReduceAddI32:
            .LFB1269:
            \t.cfi_startproc
            \tfence iorw,ow; amoadd.w.aq zero,a1,0(a0)
            \tret
            \t.cfi_endproc
            .LFE1269:
            \t.size\tNELReduceAddI32, .-NELReduceAddI32
            \t.align\t1
            \t.globl\tNELReduceAddF32
            \t.type\tNELReduceAddF32, @function
            NELReduceAddF32:
            .LFB1270:
            \t.cfi_startproc
            \taddi\tsp,sp,-16
            \t.cfi_def_cfa_offset 16
            \tfence\tiorw,iorw
            \tlw\ta5,0(a0)
            \tfence\tiorw,iorw
            \tfmv.w.x\tfa5,a5
            \taddi\ta2,sp,12
            \tsw\ta5,12(sp)
            .L131:
            \tfadd.s\tfa5,fa0,fa5
            \tlw\ta5,0(a2)
            \tfmv.x.w\ta4,fa5
            \tfence iorw,ow;  1: lr.w.aq a3,0(a0); bne a3,a5,1f; sc.w.aq a6,a4,0(a0); bnez a6,1b; 1:
            \tsubw\ta5,a3,a5
            \tseqz\ta4,a5
            \tbeq\ta5,zero,.L129
            \tsw\ta3,0(a2)
            .L129:
            \tzext.w\ta5,a4
            \tbeq\ta5,zero,.L133
            \taddi\tsp,sp,16
            \t.cfi_remember_state
            \t.cfi_def_cfa_offset 0
            \tjr\tra
            .L133:
            \t.cfi_restore_state
            \tflw\tfa5,12(sp)
            \tj\t.L131
            \t.cfi_endproc
            .LFE1270:
            \t.size\tNELReduceAddF32, .-NELReduceAddF32
            \t.bss
            \t.align\t3
            \t.set\t.LANCHOR0,. + 0
            \t.type\t_ZN12_GLOBAL__N_17workersE, @object
            \t.size\t_ZN12_GLOBAL__N_17workersE, 192
            _ZN12_GLOBAL__N_17workersE:
            \t.zero\t192
            \t.type\t_ZL13parallelCache, @object
            \t.size\t_ZL13parallelCache, 896
            _ZL13parallelCache:
            \t.zero\t896
            \t.type\t_ZL9lookupPtr, @object
            \t.size\t_ZL9lookupPtr, 4
            _ZL9lookupPtr:
            \t.zero\t4
            \t.ident\t"GCC: (Ubuntu 12.3.0-1ubuntu1~22.04) 12.3.0"
            \t.section\t.note.GNU-stack,"",@progbits
            """;
}

package backend.parallel;

import backend.riscv.RiscvFunction;
import backend.riscv.RiscvModule;

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

    public static String model = ".file\t\"NEL_sysy_rt.cpp\"\n" +
            "\t.option pic\n" +
            "\t.attribute arch, \"rv64i2p1_m2p0_a2p1_f2p2_d2p2_c2p0_zicsr2p0_zifencei2p0_zba1p0_zbb1p0\"\n" +
            "\t.attribute unaligned_access, 0\n" +
            "\t.attribute stack_align, 16\n" +
            "\t.text\n" +
            "\t.align\t1\n" +
            "\t.type\t_ZN12_GLOBAL__N_110NELWorkerEPv, @function\n" +
            "_ZN12_GLOBAL__N_110NELWorkerEPv:\n" +
            ".LFB1259:\n" +
            "\t.cfi_startproc\n" +
            "\taddi\tsp,sp,-192\n" +
            "\t.cfi_def_cfa_offset 192\n" +
            "\tsd\ts0,176(sp)\n" +
            "\t.cfi_offset 8, -16\n" +
            "\tmv\ts0,a0\n" +
            "\tsd\tra,184(sp)\n" +
            "\tsd\ts1,168(sp)\n" +
            "\tsd\ts2,160(sp)\n" +
            "\tsd\ts3,152(sp)\n" +
            "\tsd\ts4,144(sp)\n" +
            "\tsd\ts5,136(sp)\n" +
            "\t.cfi_offset 1, -8\n" +
            "\t.cfi_offset 9, -24\n" +
            "\t.cfi_offset 18, -32\n" +
            "\t.cfi_offset 19, -40\n" +
            "\t.cfi_offset 20, -48\n" +
            "\t.cfi_offset 21, -56\n" +
            "\tfence\tiorw,iorw\n" +
            "\tlw\ta5,16(a0)\n" +
            "\tfence\tiorw,iorw\n" +
            "\tli\ta4,1023\n" +
            "\tsext.w\ta2,a5\n" +
            "\tmv\ts1,sp\n" +
            "\tzext.w\ta5,a5\n" +
            "\tbgtu\ta5,a4,.L2\n" +
            "\tli\ta3,1\n" +
            "\tsrli\ta5,a5,6\n" +
            "\tsll\ta3,a3,a2\n" +
            "\tsh3add\ta5,a5,s1\n" +
            "\tld\ta4,0(a5)\n" +
            "\tor\ta4,a4,a3\n" +
            "\tsd\ta4,0(a5)\n" +
            ".L2:\n" +
            "\tli\ta0,178\n" +
            "\taddi\ts2,s0,20\n" +
            "\tcall\tsyscall@plt\n" +
            "\taddi\ts3,s0,40\n" +
            "\tmv\ta2,s1\n" +
            "\tsext.w\ta0,a0\n" +
            "\tli\ta1,128\n" +
            "\taddi\ts1,s0,44\n" +
            "\tcall\tsched_setaffinity@plt\n" +
            "\tli\ts4,1\n" +
            "\tj\t.L6\n" +
            ".L21:\n" +
            "\tfence iorw,ow;  1: lr.w.aq a5,0(s3); bne a5,s4,1f; sc.w.aq a4,zero,0(s3); bnez a4,1b; 1:\n" +
            "\taddiw\ta5,a5,-1\n" +
            "\tli\ts5,1\n" +
            "\tbne\ta5,zero,.L4\n" +
            ".L7:\n" +
            "\tfence\tiorw,iorw\n" +
            "\tlw\ta5,0(s2)\n" +
            "\tfence\tiorw,iorw\n" +
            "\tbeq\ta5,zero,.L5\n" +
            "\tfence\tiorw,iorw\n" +
            "\tfence\tiorw,iorw\n" +
            "\tld\ta5,24(s0)\n" +
            "\tfence\tiorw,iorw\n" +
            "\tfence\tiorw,iorw\n" +
            "\tlw\ta0,32(s0)\n" +
            "\tfence\tiorw,iorw\n" +
            "\tfence\tiorw,iorw\n" +
            "\tsext.w\ta0,a0\n" +
            "\tlw\ta1,36(s0)\n" +
            "\tfence\tiorw,iorw\n" +
            "\tjalr\ta5\n" +
            "\tfence\tiorw,iorw\n" +
            "\tfence iorw,ow;  1: lr.w.aq a5,0(s1); bne a5,zero,1f; sc.w.aq a4,s4,0(s1); bnez a4,1b; 1:\n" +
            "\tsext.w\ta5,a5\n" +
            "\tbne\ta5,zero,.L6\n" +
            "\tmv\ta1,s1\n" +
            "\tli\ta6,0\n" +
            "\tli\ta4,0\n" +
            "\tli\ta3,1\n" +
            "\tli\ta2,1\n" +
            "\tli\ta0,98\n" +
            "\tcall\tsyscall@plt\n" +
            ".L6:\n" +
            "\tfence\tiorw,iorw\n" +
            "\tlw\ta5,0(s2)\n" +
            "\tfence\tiorw,iorw\n" +
            "\tbne\ta5,zero,.L21\n" +
            ".L5:\n" +
            "\tld\tra,184(sp)\n" +
            "\t.cfi_remember_state\n" +
            "\t.cfi_restore 1\n" +
            "\tli\ta0,0\n" +
            "\tld\ts0,176(sp)\n" +
            "\t.cfi_restore 8\n" +
            "\tld\ts1,168(sp)\n" +
            "\t.cfi_restore 9\n" +
            "\tld\ts2,160(sp)\n" +
            "\t.cfi_restore 18\n" +
            "\tld\ts3,152(sp)\n" +
            "\t.cfi_restore 19\n" +
            "\tld\ts4,144(sp)\n" +
            "\t.cfi_restore 20\n" +
            "\tld\ts5,136(sp)\n" +
            "\t.cfi_restore 21\n" +
            "\taddi\tsp,sp,192\n" +
            "\t.cfi_def_cfa_offset 0\n" +
            "\tjr\tra\n" +
            ".L4:\n" +
            "\t.cfi_restore_state\n" +
            "\tmv\ta1,s3\n" +
            "\tli\ta6,0\n" +
            "\tli\ta5,0\n" +
            "\tli\ta4,0\n" +
            "\tli\ta3,0\n" +
            "\tli\ta2,0\n" +
            "\tli\ta0,98\n" +
            "\tcall\tsyscall@plt\n" +
            "\tfence iorw,ow;  1: lr.w.aq a5,0(s3); bne a5,s5,1f; sc.w.aq a4,zero,0(s3); bnez a4,1b; 1:\n" +
            "\taddiw\ta5,a5,-1\n" +
            "\tbeq\ta5,zero,.L7\n" +
            "\tj\t.L4\n" +
            "\t.cfi_endproc\n" +
            ".LFE1259:\n" +
            "\t.size\t_ZN12_GLOBAL__N_110NELWorkerEPv, .-_ZN12_GLOBAL__N_110NELWorkerEPv\n" +
            "\t.section\t.text.startup,\"ax\",@progbits\n" +
            "\t.align\t1\n" +
            "\t.globl\tNELInitRuntime\n" +
            "\t.type\tNELInitRuntime, @function\n" +
            "NELInitRuntime:\n" +
            ".LFB1260:\n" +
            "\t.cfi_startproc\n" +
            "\taddi\tsp,sp,-64\n" +
            "\t.cfi_def_cfa_offset 64\n" +
            "\tsd\ts2,32(sp)\n" +
            "\t.cfi_offset 18, -32\n" +
            "\tli\ts2,331776\n" +
            "\tsd\ts3,24(sp)\n" +
            "\t.cfi_offset 19, -40\n" +
            "\tli\ts3,131072\n" +
            "\taddi\ts3,s3,34\n" +
            "\taddi\ts2,s2,-256\n" +
            "\tsd\ts0,48(sp)\n" +
            "\t.cfi_offset 8, -16\n" +
            "\tlla\ts0,.LANCHOR0\n" +
            "\tsd\ts1,40(sp)\n" +
            "\t.cfi_offset 9, -24\n" +
            "\tli\ts1,0\n" +
            "\tsd\ts4,16(sp)\n" +
            "\t.cfi_offset 20, -48\n" +
            "\tli\ts4,4\n" +
            "\tsd\ts5,8(sp)\n" +
            "\t.cfi_offset 21, -56\n" +
            "\tlla\ts5,_ZN12_GLOBAL__N_110NELWorkerEPv\n" +
            "\tsd\ts6,0(sp)\n" +
            "\t.cfi_offset 22, -64\n" +
            "\tli\ts6,1\n" +
            "\tsd\tra,56(sp)\n" +
            "\t.cfi_offset 1, -8\n" +
            ".L23:\n" +
            "\taddi\ta5,s0,20\n" +
            "\tfence iorw,ow; amoswap.w.aq zero,s6,0(a5)\n" +
            "\tmv\ta3,s3\n" +
            "\tli\ta5,0\n" +
            "\tli\ta4,-1\n" +
            "\tli\ta2,3\n" +
            "\tli\ta1,1048576\n" +
            "\tli\ta0,0\n" +
            "\tcall\tmmap@plt\n" +
            "\tsd\ta0,8(s0)\n" +
            "\taddi\ta5,s0,16\n" +
            "\tfence iorw,ow; amoswap.w.aq zero,s1,0(a5)\n" +
            "\tli\ta5,1048576\n" +
            "\tmv\ta3,s0\n" +
            "\tld\ta1,8(s0)\n" +
            "\tmv\ta2,s2\n" +
            "\tadd\ta1,a1,a5\n" +
            "\tmv\ta0,s5\n" +
            "\taddi\ts0,s0,48\n" +
            "\taddiw\ts1,s1,1\n" +
            "\tcall\tclone@plt\n" +
            "\tsw\ta0,-48(s0)\n" +
            "\tbne\ts1,s4,.L23\n" +
            "\tld\tra,56(sp)\n" +
            "\t.cfi_restore 1\n" +
            "\tld\ts0,48(sp)\n" +
            "\t.cfi_restore 8\n" +
            "\tld\ts1,40(sp)\n" +
            "\t.cfi_restore 9\n" +
            "\tld\ts2,32(sp)\n" +
            "\t.cfi_restore 18\n" +
            "\tld\ts3,24(sp)\n" +
            "\t.cfi_restore 19\n" +
            "\tld\ts4,16(sp)\n" +
            "\t.cfi_restore 20\n" +
            "\tld\ts5,8(sp)\n" +
            "\t.cfi_restore 21\n" +
            "\tld\ts6,0(sp)\n" +
            "\t.cfi_restore 22\n" +
            "\taddi\tsp,sp,64\n" +
            "\t.cfi_def_cfa_offset 0\n" +
            "\tjr\tra\n" +
            "\t.cfi_endproc\n" +
            ".LFE1260:\n" +
            "\t.size\tNELInitRuntime, .-NELInitRuntime\n" +
            "\t.section\t.init_array,\"aw\"\n" +
            "\t.align\t3\n" +
            "\t.dword\tNELInitRuntime\n" +
            "\t.section\t.text.exit,\"ax\",@progbits\n" +
            "\t.align\t1\n" +
            "\t.globl\tNELUninitRuntime\n" +
            "\t.type\tNELUninitRuntime, @function\n" +
            "NELUninitRuntime:\n" +
            ".LFB1261:\n" +
            "\t.cfi_startproc\n" +
            "\taddi\tsp,sp,-32\n" +
            "\t.cfi_def_cfa_offset 32\n" +
            "\tsd\ts0,16(sp)\n" +
            "\t.cfi_offset 8, -16\n" +
            "\tlla\ts0,.LANCHOR0+40\n" +
            "\tsd\ts1,8(sp)\n" +
            "\t.cfi_offset 9, -24\n" +
            "\tli\ts1,1\n" +
            "\tsd\ts2,0(sp)\n" +
            "\t.cfi_offset 18, -32\n" +
            "\tlla\ts2,.LANCHOR0+232\n" +
            "\tsd\tra,24(sp)\n" +
            "\t.cfi_offset 1, -8\n" +
            ".L30:\n" +
            "\taddi\ta5,s0,-20\n" +
            "\tfence iorw,ow; amoswap.w.aq zero,zero,0(a5)\n" +
            "\tfence iorw,ow;  1: lr.w.aq a7,0(s0); bne a7,zero,1f; sc.w.aq a5,s1,0(s0); bnez a5,1b; 1:\n" +
            "\tsext.w\ta7,a7\n" +
            "\tmv\ta1,s0\n" +
            "\tli\ta6,0\n" +
            "\tli\ta5,0\n" +
            "\tli\ta4,0\n" +
            "\tli\ta3,1\n" +
            "\tli\ta2,1\n" +
            "\tli\ta0,98\n" +
            "\tbne\ta7,zero,.L27\n" +
            "\taddi\ts0,s0,48\n" +
            "\tcall\tsyscall@plt\n" +
            "\tli\ta2,0\n" +
            "\tli\ta1,0\n" +
            "\tlw\ta0,-88(s0)\n" +
            "\tcall\twaitpid@plt\n" +
            "\tbne\ts2,s0,.L30\n" +
            ".L26:\n" +
            "\tld\tra,24(sp)\n" +
            "\t.cfi_remember_state\n" +
            "\t.cfi_restore 1\n" +
            "\tld\ts0,16(sp)\n" +
            "\t.cfi_restore 8\n" +
            "\tld\ts1,8(sp)\n" +
            "\t.cfi_restore 9\n" +
            "\tld\ts2,0(sp)\n" +
            "\t.cfi_restore 18\n" +
            "\taddi\tsp,sp,32\n" +
            "\t.cfi_def_cfa_offset 0\n" +
            "\tjr\tra\n" +
            ".L27:\n" +
            "\t.cfi_restore_state\n" +
            "\tlw\ta0,-40(s0)\n" +
            "\tli\ta2,0\n" +
            "\taddi\ts0,s0,48\n" +
            "\tli\ta1,0\n" +
            "\tcall\twaitpid@plt\n" +
            "\tbne\ts0,s2,.L30\n" +
            "\tj\t.L26\n" +
            "\t.cfi_endproc\n" +
            ".LFE1261:\n" +
            "\t.size\tNELUninitRuntime, .-NELUninitRuntime\n" +
            "\t.section\t.fini_array,\"aw\"\n" +
            "\t.align\t3\n" +
            "\t.dword\tNELUninitRuntime\n" +
            "\t.text\n" +
            "\t.align\t1\n" +
            "\t.globl\tNELParallelFor\n" +
            "\t.type\tNELParallelFor, @function\n" +
            "NELParallelFor:\n" +
            ".LFB1265:\n" +
            "\t.cfi_startproc\n" +
            "\tbge\ta0,a1,.L116\n" +
            "\taddi\tsp,sp,-144\n" +
            "\t.cfi_def_cfa_offset 144\n" +
            "\tsubw\tt4,a1,a0\n" +
            "\tli\ta5,15\n" +
            "\tsd\ts2,112(sp)\n" +
            "\t.cfi_offset 18, -32\n" +
            "\tmv\ts2,a1\n" +
            "\tsd\ts3,104(sp)\n" +
            "\t.cfi_offset 19, -40\n" +
            "\tmv\ts3,a2\n" +
            "\tsd\ts5,88(sp)\n" +
            "\t.cfi_offset 21, -56\n" +
            "\tmv\ts5,t4\n" +
            "\tsd\ts11,40(sp)\n" +
            "\t.cfi_offset 27, -104\n" +
            "\tmv\ts11,a0\n" +
            "\tsd\tra,136(sp)\n" +
            "\tsd\ts0,128(sp)\n" +
            "\tsd\ts1,120(sp)\n" +
            "\tsd\ts4,96(sp)\n" +
            "\tsd\ts6,80(sp)\n" +
            "\tsd\ts7,72(sp)\n" +
            "\tsd\ts8,64(sp)\n" +
            "\tsd\ts9,56(sp)\n" +
            "\tsd\ts10,48(sp)\n" +
            "\t.cfi_offset 1, -8\n" +
            "\t.cfi_offset 8, -16\n" +
            "\t.cfi_offset 9, -24\n" +
            "\t.cfi_offset 20, -48\n" +
            "\t.cfi_offset 22, -64\n" +
            "\t.cfi_offset 23, -72\n" +
            "\t.cfi_offset 24, -80\n" +
            "\t.cfi_offset 25, -88\n" +
            "\t.cfi_offset 26, -96\n" +
            "\tbgt\tt4,a5,.L34\n" +
            "\tld\tra,136(sp)\n" +
            "\t.cfi_remember_state\n" +
            "\t.cfi_restore 1\n" +
            "\tld\ts0,128(sp)\n" +
            "\t.cfi_restore 8\n" +
            "\tld\ts1,120(sp)\n" +
            "\t.cfi_restore 9\n" +
            "\tld\ts2,112(sp)\n" +
            "\t.cfi_restore 18\n" +
            "\tld\ts3,104(sp)\n" +
            "\t.cfi_restore 19\n" +
            "\tld\ts4,96(sp)\n" +
            "\t.cfi_restore 20\n" +
            "\tld\ts5,88(sp)\n" +
            "\t.cfi_restore 21\n" +
            "\tld\ts6,80(sp)\n" +
            "\t.cfi_restore 22\n" +
            "\tld\ts7,72(sp)\n" +
            "\t.cfi_restore 23\n" +
            "\tld\ts8,64(sp)\n" +
            "\t.cfi_restore 24\n" +
            "\tld\ts9,56(sp)\n" +
            "\t.cfi_restore 25\n" +
            "\tld\ts10,48(sp)\n" +
            "\t.cfi_restore 26\n" +
            "\tld\ts11,40(sp)\n" +
            "\t.cfi_restore 27\n" +
            "\taddi\tsp,sp,144\n" +
            "\t.cfi_def_cfa_offset 0\n" +
            "\tjr\ta2\n" +
            ".L34:\n" +
            "\t.cfi_restore_state\n" +
            "\tlla\ta7,.LANCHOR0\n" +
            "\tli\ta3,16\n" +
            "\tlw\ta4,1088(a7)\n" +
            "\tli\ta1,0\n" +
            "\tlla\ta2,.LANCHOR0+192\n" +
            "\tli\tt3,16\n" +
            ".L39:\n" +
            "\tbeq\ta4,t3,.L80\n" +
            "\tzext.w\ta6,a4\n" +
            "\tslli.uw\ta5,a4,3\n" +
            "\tsub\ta5,a5,a6\n" +
            "\tsh3add\ts0,a5,a2\n" +
            ".L35:\n" +
            "\tslli\ta5,a6,3\n" +
            "\taddiw\ta3,a3,-1\n" +
            "\tsub\ta5,a5,a6\n" +
            "\tsh3add\ta5,a5,a7\n" +
            "\tlbu\tt1,204(a5)\n" +
            "\tbeq\tt1,zero,.L36\n" +
            "\tld\tt1,192(a5)\n" +
            "\tbeq\tt1,s3,.L119\n" +
            ".L36:\n" +
            "\taddiw\ta4,a4,1\n" +
            "\tli\ta1,1\n" +
            "\tbne\ta3,zero,.L39\n" +
            "\tlbu\ta5,204(a7)\n" +
            "\tbeq\ta5,zero,.L40\n" +
            "\tlbu\ta5,260(a7)\n" +
            "\tbeq\ta5,zero,.L81\n" +
            "\tlbu\ta5,316(a7)\n" +
            "\tbeq\ta5,zero,.L82\n" +
            "\tlbu\ta5,372(a7)\n" +
            "\tbeq\ta5,zero,.L83\n" +
            "\tlbu\ta5,428(a7)\n" +
            "\tbeq\ta5,zero,.L84\n" +
            "\tlbu\ta5,484(a7)\n" +
            "\tbeq\ta5,zero,.L85\n" +
            "\tlbu\ta5,540(a7)\n" +
            "\tbeq\ta5,zero,.L86\n" +
            "\tlbu\ta5,596(a7)\n" +
            "\tbeq\ta5,zero,.L87\n" +
            "\tlbu\ta5,652(a7)\n" +
            "\tbeq\ta5,zero,.L88\n" +
            "\tlbu\ta5,708(a7)\n" +
            "\tbeq\ta5,zero,.L89\n" +
            "\tlbu\ta5,764(a7)\n" +
            "\tbeq\ta5,zero,.L90\n" +
            "\tlbu\ta5,820(a7)\n" +
            "\tbeq\ta5,zero,.L91\n" +
            "\tlbu\ta5,876(a7)\n" +
            "\tbeq\ta5,zero,.L92\n" +
            "\tlbu\ta5,932(a7)\n" +
            "\tbeq\ta5,zero,.L93\n" +
            "\tlbu\ta5,988(a7)\n" +
            "\tbeq\ta5,zero,.L94\n" +
            "\tlbu\ta5,1044(a7)\n" +
            "\tli\ta3,15\n" +
            "\tbeq\ta5,zero,.L40\n" +
            "\tlw\ta1,264(a7)\n" +
            "\tlw\ta5,208(a7)\n" +
            "\tlw\ta6,320(a7)\n" +
            "\tsgtu\ta3,a5,a1\n" +
            "\tlw\tt1,376(a7)\n" +
            "\tbleu a5,a1,1f; mv a5,a1; 1: # movcc\n" +
            "\tmv\ta0,a5\n" +
            "\tbgeu a6,a5,1f; mv a0,a6; 1: # movcc\n" +
            "\tlw\ta4,432(a7)\n" +
            "\tbgeu a6,a5,1f; li a3,2; 1: # movcc\n" +
            "\tmv\ta5,a0\n" +
            "\tbgeu t1,a0,1f; mv a5,t1; 1: # movcc\n" +
            "\tlw\ta1,488(a7)\n" +
            "\tbgeu t1,a0,1f; li a3,3; 1: # movcc\n" +
            "\tmv\ta0,a5\n" +
            "\tbgeu a4,a5,1f; mv a0,a4; 1: # movcc\n" +
            "\tlw\ta6,544(a7)\n" +
            "\tbgeu a4,a5,1f; li a3,4; 1: # movcc\n" +
            "\tmv\ta5,a0\n" +
            "\tbleu a0,a1,1f; mv a5,a1; 1: # movcc\n" +
            "\tlw\tt1,600(a7)\n" +
            "\tbleu a0,a1,1f; li a3,5; 1: # movcc\n" +
            "\tmv\ta0,a5\n" +
            "\tbgeu a6,a5,1f; mv a0,a6; 1: # movcc\n" +
            "\tlw\ta4,656(a7)\n" +
            "\tbgeu a6,a5,1f; li a3,6; 1: # movcc\n" +
            "\tmv\ta6,a0\n" +
            "\tbgeu t1,a0,1f; mv a6,t1; 1: # movcc\n" +
            "\tlw\ta1,712(a7)\n" +
            "\tbgeu t1,a0,1f; li a3,7; 1: # movcc\n" +
            "\tmv\tt1,a6\n" +
            "\tbleu a6,a4,1f; mv t1,a4; 1: # movcc\n" +
            "\tlw\ta5,768(a7)\n" +
            "\tbleu a6,a4,1f; li a3,8; 1: # movcc\n" +
            "\tmv\ta6,t1\n" +
            "\tbleu t1,a1,1f; mv a6,a1; 1: # movcc\n" +
            "\tlw\ta0,824(a7)\n" +
            "\tbleu t1,a1,1f; li a3,9; 1: # movcc\n" +
            "\tmv\tt1,a6\n" +
            "\tbleu a6,a5,1f; mv t1,a5; 1: # movcc\n" +
            "\tlw\ta4,880(a7)\n" +
            "\tbleu a6,a5,1f; li a3,10; 1: # movcc\n" +
            "\tmv\ta6,t1\n" +
            "\tbleu t1,a0,1f; mv a6,a0; 1: # movcc\n" +
            "\tlw\ta1,936(a7)\n" +
            "\tbleu t1,a0,1f; li a3,11; 1: # movcc\n" +
            "\tmv\ta0,a6\n" +
            "\tbleu a6,a4,1f; mv a0,a4; 1: # movcc\n" +
            "\tlw\ta5,992(a7)\n" +
            "\tbleu a6,a4,1f; li a3,12; 1: # movcc\n" +
            "\tmv\ta4,a0\n" +
            "\tbleu a0,a1,1f; mv a4,a1; 1: # movcc\n" +
            "\tlw\tt1,1048(a7)\n" +
            "\tbleu a0,a1,1f; li a3,13; 1: # movcc\n" +
            "\tmv\ta1,a4\n" +
            "\tbleu a4,a5,1f; mv a1,a5; 1: # movcc\n" +
            "\tbleu a4,a5,1f; li a3,14; 1: # movcc\n" +
            "\tbgeu t1,a1,1f; li a3,15; 1: # movcc\n" +
            "\tslli\ta5,a3,3\n" +
            "\tsub\ta5,a5,a3\n" +
            "\tsh3add\ta4,a5,a7\n" +
            "\tsh3add\ts0,a5,a2\n" +
            "\tli\ta5,1\n" +
            "\tsw\ta3,1088(a7)\n" +
            "\tsd\ts3,192(a4)\n" +
            "\tsw\ts5,200(a4)\n" +
            "\tsw\ta5,208(a4)\n" +
            ".L38:\n" +
            "\tlw\ts6,16(s0)\n" +
            "\tli\ta5,99\n" +
            "\tbleu\ts6,a5,.L57\n" +
            "\tli\ta5,159\n" +
            "\tbleu\ts6,a5,.L120\n" +
            "\tlw\ts6,48(s0)\n" +
            "\tbeq\ts6,zero,.L60\n" +
            "\tli\tt3,1\n" +
            "\tsllw\ts1,t3,s6\n" +
            "\tmv\ts8,s1\n" +
            ".L61:\n" +
            "\tsd\tzero,0(sp)\n" +
            ".L59:\n" +
            "\tli\ta5,1\n" +
            "\tbne\ts8,a5,.L64\n" +
            "\tmv\ta1,s2\n" +
            "\tmv\ta0,s11\n" +
            "\tjalr\ts3\n" +
            "\tld\ta5,0(sp)\n" +
            "\tbne\ta5,zero,.L121\n" +
            ".L32:\n" +
            "\tld\tra,136(sp)\n" +
            "\t.cfi_remember_state\n" +
            "\t.cfi_restore 1\n" +
            "\tld\ts0,128(sp)\n" +
            "\t.cfi_restore 8\n" +
            "\tld\ts1,120(sp)\n" +
            "\t.cfi_restore 9\n" +
            "\tld\ts2,112(sp)\n" +
            "\t.cfi_restore 18\n" +
            "\tld\ts3,104(sp)\n" +
            "\t.cfi_restore 19\n" +
            "\tld\ts4,96(sp)\n" +
            "\t.cfi_restore 20\n" +
            "\tld\ts5,88(sp)\n" +
            "\t.cfi_restore 21\n" +
            "\tld\ts6,80(sp)\n" +
            "\t.cfi_restore 22\n" +
            "\tld\ts7,72(sp)\n" +
            "\t.cfi_restore 23\n" +
            "\tld\ts8,64(sp)\n" +
            "\t.cfi_restore 24\n" +
            "\tld\ts9,56(sp)\n" +
            "\t.cfi_restore 25\n" +
            "\tld\ts10,48(sp)\n" +
            "\t.cfi_restore 26\n" +
            "\tld\ts11,40(sp)\n" +
            "\t.cfi_restore 27\n" +
            "\taddi\tsp,sp,144\n" +
            "\t.cfi_def_cfa_offset 0\n" +
            "\tjr\tra\n" +
            ".L80:\n" +
            "\t.cfi_restore_state\n" +
            "\tmv\ts0,a2\n" +
            "\tli\ta6,0\n" +
            "\tli\ta1,1\n" +
            "\tli\ta4,0\n" +
            "\tj\t.L35\n" +
            ".L119:\n" +
            "\tlw\ta5,200(a5)\n" +
            "\tbne\ta5,t4,.L36\n" +
            "\tbeq\ta1,zero,.L37\n" +
            "\tsw\ta4,1088(a7)\n" +
            ".L37:\n" +
            "\tslli\ta5,a6,3\n" +
            "\tsub\ta5,a5,a6\n" +
            "\tsh3add\ta5,a5,a7\n" +
            "\tlw\ta4,208(a5)\n" +
            "\taddiw\ta4,a4,1\n" +
            "\tsw\ta4,208(a5)\n" +
            "\tj\t.L38\n" +
            ".L116:\n" +
            "\t.cfi_def_cfa_offset 0\n" +
            "\t.cfi_restore 1\n" +
            "\t.cfi_restore 8\n" +
            "\t.cfi_restore 9\n" +
            "\t.cfi_restore 18\n" +
            "\t.cfi_restore 19\n" +
            "\t.cfi_restore 20\n" +
            "\t.cfi_restore 21\n" +
            "\t.cfi_restore 22\n" +
            "\t.cfi_restore 23\n" +
            "\t.cfi_restore 24\n" +
            "\t.cfi_restore 25\n" +
            "\t.cfi_restore 26\n" +
            "\t.cfi_restore 27\n" +
            "\tret\n" +
            ".L60:\n" +
            "\t.cfi_def_cfa_offset 144\n" +
            "\t.cfi_offset 1, -8\n" +
            "\t.cfi_offset 8, -16\n" +
            "\t.cfi_offset 9, -24\n" +
            "\t.cfi_offset 18, -32\n" +
            "\t.cfi_offset 19, -40\n" +
            "\t.cfi_offset 20, -48\n" +
            "\t.cfi_offset 21, -56\n" +
            "\t.cfi_offset 22, -64\n" +
            "\t.cfi_offset 23, -72\n" +
            "\t.cfi_offset 24, -80\n" +
            "\t.cfi_offset 25, -88\n" +
            "\t.cfi_offset 26, -96\n" +
            "\t.cfi_offset 27, -104\n" +
            "\tld\ta5,24(s0)\n" +
            "\tli\ts8,1\n" +
            "\tld\ta4,32(s0)\n" +
            "\tli\ts1,1\n" +
            "\tbge\ta4,a5,.L62\n" +
            "\tmv\ta5,a4\n" +
            "\tli\ts8,2\n" +
            "\tli\ts1,2\n" +
            "\tli\ts6,1\n" +
            ".L62:\n" +
            "\tld\ta4,40(s0)\n" +
            "\tble\ta5,a4,.L63\n" +
            "\tli\ta5,2\n" +
            "\tsw\ta5,48(s0)\n" +
            ".L57:\n" +
            "\tfence\tiorw,iorw\n" +
            "\tsrliw\ts5,s5,2\n" +
            "\tli\ts6,2\n" +
            "\taddiw\ts5,s5,3\n" +
            "\tli\ts8,4\n" +
            "\tandi\ts5,s5,-4\n" +
            "\tli\ts1,4\n" +
            "\tsext.w\ts5,s5\n" +
            "\tsd\tzero,0(sp)\n" +
            "\tsw\tzero,16(sp)\n" +
            ".L79:\n" +
            "\taddiw\ts7,s8,-1\n" +
            "\tsext.w\ts11,s11\n" +
            "\taddi\ts9,sp,16\n" +
            "\tlla\ts4,.LANCHOR0+40\n" +
            "\tli\ts10,0\n" +
            ".L72:\n" +
            "\tsext.w\ta4,s11\n" +
            "\taddw\ts11,s5,s11\n" +
            "\tmin\ta5,s11,s2\n" +
            "\tbne s7,s10,1f; mv a5,s2; 1: # movcc\n" +
            "\tble\ta5,a4,.L70\n" +
            "\taddi\ta3,s4,-16\n" +
            "\tfence iorw,ow; amoswap.d.aq zero,s3,0(a3)\n" +
            "\taddi\ta3,s4,-8\n" +
            "\tfence iorw,ow; amoswap.w.aq zero,a4,0(a3)\n" +
            "\taddi\ta4,s4,-4\n" +
            "\tfence iorw,ow; amoswap.w.aq zero,a5,0(a4)\n" +
            "\tli\ta4,1\n" +
            "\tfence iorw,ow;  1: lr.w.aq t5,0(s4); bne t5,zero,1f; sc.w.aq a5,a4,0(s4); bnez a5,1b; 1:\n" +
            "\tsext.w\tt5,t5\n" +
            "\tmv\ta1,s4\n" +
            "\tli\ta6,0\n" +
            "\tli\ta5,0\n" +
            "\tli\ta4,0\n" +
            "\tli\ta3,1\n" +
            "\tli\ta2,1\n" +
            "\tli\ta0,98\n" +
            "\tbne\tt5,zero,.L71\n" +
            "\tcall\tsyscall@plt\n" +
            ".L71:\n" +
            "\tli\ta5,1\n" +
            "\tsb\ta5,0(s9)\n" +
            ".L70:\n" +
            "\taddiw\ts10,s10,1\n" +
            "\taddi\ts9,s9,1\n" +
            "\taddi\ts4,s4,48\n" +
            "\tbne\ts1,s10,.L72\n" +
            ".L73:\n" +
            "\tbeq\ts8,zero,.L68\n" +
            "\tlla\ts3,.LANCHOR0+44\n" +
            "\tli\ts4,1\n" +
            "\taddi\ts2,sp,16\n" +
            "\tadd.uw\ts8,s8,s2\n" +
            ".L75:\n" +
            "\tlbu\ta5,0(s2)\n" +
            "\tbne\ta5,zero,.L74\n" +
            ".L76:\n" +
            "\taddi\ts2,s2,1\n" +
            "\taddi\ts3,s3,48\n" +
            "\tbne\ts2,s8,.L75\n" +
            ".L68:\n" +
            "\tfence\tiorw,iorw\n" +
            "\tld\ta5,0(sp)\n" +
            "\tbeq\ta5,zero,.L32\n" +
            ".L121:\n" +
            "\taddi\ta1,sp,16\n" +
            "\tli\ta0,1\n" +
            "\tsh3add.uw\ts6,s6,s0\n" +
            "\tcall\tclock_gettime@plt\n" +
            "\tld\ta5,16(sp)\n" +
            "\tli\ta4,1000001536\n" +
            "\taddi\ta4,a4,-1536\n" +
            "\tld\ta3,24(sp)\n" +
            "\tmul\ta5,a5,a4\n" +
            "\tld\ta4,24(s6)\n" +
            "\tadd\ta5,a5,a3\n" +
            "\tld\ta3,8(sp)\n" +
            "\tsub\ta5,a5,a3\n" +
            "\tadd\ta5,a4,a5\n" +
            "\tsd\ta5,24(s6)\n" +
            "\tj\t.L32\n" +
            ".L74:\n" +
            "\tfence iorw,ow;  1: lr.w.aq a5,0(s3); bne a5,s4,1f; sc.w.aq a4,zero,0(s3); bnez a4,1b; 1:\n" +
            "\taddiw\ta5,a5,-1\n" +
            "\tbeq\ta5,zero,.L76\n" +
            "\tli\ts5,1\n" +
            ".L77:\n" +
            "\tmv\ta1,s3\n" +
            "\tli\ta6,0\n" +
            "\tli\ta5,0\n" +
            "\tli\ta4,0\n" +
            "\tli\ta3,0\n" +
            "\tli\ta2,0\n" +
            "\tli\ta0,98\n" +
            "\tcall\tsyscall@plt\n" +
            "\tfence iorw,ow;  1: lr.w.aq a5,0(s3); bne a5,s5,1f; sc.w.aq a4,zero,0(s3); bnez a4,1b; 1:\n" +
            "\taddiw\ta5,a5,-1\n" +
            "\tbeq\ta5,zero,.L76\n" +
            "\tj\t.L77\n" +
            ".L81:\n" +
            "\tli\ta3,1\n" +
            ".L40:\n" +
            "\tli\ta4,1\n" +
            "\tzext.w\ta1,a3\n" +
            "\tsw\ta3,1088(a7)\n" +
            "\tslli.uw\ta5,a3,3\n" +
            "\tsub\ta5,a5,a1\n" +
            "\tsh3add\ta7,a5,a7\n" +
            "\tsh3add\ts0,a5,a2\n" +
            "\tsb\ta4,204(a7)\n" +
            "\tsd\ts3,192(a7)\n" +
            "\tsw\ts5,200(a7)\n" +
            "\tsw\ta4,208(a7)\n" +
            "\tj\t.L38\n" +
            ".L120:\n" +
            "\taddiw\ts6,s6,-100\n" +
            "\tli\ta5,20\n" +
            "\taddi\ta1,sp,16\n" +
            "\tli\ta0,1\n" +
            "\tdivuw\ts6,s6,a5\n" +
            "\tli\ta5,1\n" +
            "\tsd\ta5,0(sp)\n" +
            "\tcall\tclock_gettime@plt\n" +
            "\tli\ta5,1000001536\n" +
            "\tli\tt3,1\n" +
            "\tld\ts1,16(sp)\n" +
            "\taddi\ta5,a5,-1536\n" +
            "\tmul\ts1,s1,a5\n" +
            "\tld\ta5,24(sp)\n" +
            "\tadd\ta5,s1,a5\n" +
            "\tsd\ta5,8(sp)\n" +
            "\tsllw\ts1,t3,s6\n" +
            "\tmv\ts8,s1\n" +
            "\tj\t.L59\n" +
            ".L64:\n" +
            "\tfence\tiorw,iorw\n" +
            "\tsw\tzero,16(sp)\n" +
            "\tble\ts1,zero,.L73\n" +
            "\tsrlw\ts5,s5,s6\n" +
            "\taddiw\ts5,s5,3\n" +
            "\tandi\ts5,s5,-4\n" +
            "\tsext.w\ts5,s5\n" +
            "\tj\t.L79\n" +
            ".L63:\n" +
            "\tsw\ts6,48(s0)\n" +
            "\tj\t.L61\n" +
            ".L85:\n" +
            "\tli\ta3,5\n" +
            "\tj\t.L40\n" +
            ".L82:\n" +
            "\tli\ta3,2\n" +
            "\tj\t.L40\n" +
            ".L83:\n" +
            "\tli\ta3,3\n" +
            "\tj\t.L40\n" +
            ".L84:\n" +
            "\tli\ta3,4\n" +
            "\tj\t.L40\n" +
            ".L86:\n" +
            "\tli\ta3,6\n" +
            "\tj\t.L40\n" +
            ".L87:\n" +
            "\tli\ta3,7\n" +
            "\tj\t.L40\n" +
            ".L88:\n" +
            "\tli\ta3,8\n" +
            "\tj\t.L40\n" +
            ".L89:\n" +
            "\tli\ta3,9\n" +
            "\tj\t.L40\n" +
            ".L90:\n" +
            "\tli\ta3,10\n" +
            "\tj\t.L40\n" +
            ".L91:\n" +
            "\tli\ta3,11\n" +
            "\tj\t.L40\n" +
            ".L92:\n" +
            "\tli\ta3,12\n" +
            "\tj\t.L40\n" +
            ".L93:\n" +
            "\tli\ta3,13\n" +
            "\tj\t.L40\n" +
            ".L94:\n" +
            "\tli\ta3,14\n" +
            "\tj\t.L40\n" +
            "\t.cfi_endproc\n" +
            ".LFE1265:\n" +
            "\t.size\tNELParallelFor, .-NELParallelFor\n" +
            "\t.align\t1\n" +
            "\t.globl\tNELCacheLookup\n" +
            "\t.type\tNELCacheLookup, @function\n" +
            "NELCacheLookup:\n" +
            ".LFB1267:\n" +
            "\t.cfi_startproc\n" +
            "\tslli\ta1,a1,32\n" +
            "\tli\ta5,1021\n" +
            "\tor\ta2,a1,a2\n" +
            "\tremu\ta5,a2,a5\n" +
            "\tslli\ta5,a5,4\n" +
            "\tadd\ta0,a0,a5\n" +
            "\tlw\ta5,12(a0)\n" +
            "\tbeq\ta5,zero,.L125\n" +
            "\tld\ta5,0(a0)\n" +
            "\tbeq\ta5,a2,.L122\n" +
            "\tsw\tzero,12(a0)\n" +
            ".L125:\n" +
            "\tsd\ta2,0(a0)\n" +
            ".L122:\n" +
            "\tret\n" +
            "\t.cfi_endproc\n" +
            ".LFE1267:\n" +
            "\t.size\tNELCacheLookup, .-NELCacheLookup\n" +
            "\t.align\t1\n" +
            "\t.globl\tNELAddRec3SRem\n" +
            "\t.type\tNELAddRec3SRem, @function\n" +
            "NELAddRec3SRem:\n" +
            ".LFB1268:\n" +
            "\t.cfi_startproc\n" +
            "\taddi\ta5,a0,-1\n" +
            "\tmul\ta5,a5,a0\n" +
            "\tsrli\ta0,a5,63\n" +
            "\tadd\ta0,a0,a5\n" +
            "\tsrai\ta0,a0,1\n" +
            "\trem\ta0,a0,a1\n" +
            "\tsext.w\ta0,a0\n" +
            "\tret\n" +
            "\t.cfi_endproc\n" +
            ".LFE1268:\n" +
            "\t.size\tNELAddRec3SRem, .-NELAddRec3SRem\n" +
            "\t.align\t1\n" +
            "\t.globl\tNELReduceAddI32\n" +
            "\t.type\tNELReduceAddI32, @function\n" +
            "NELReduceAddI32:\n" +
            ".LFB1269:\n" +
            "\t.cfi_startproc\n" +
            "\tfence iorw,ow; amoadd.w.aq zero,a1,0(a0)\n" +
            "\tret\n" +
            "\t.cfi_endproc\n" +
            ".LFE1269:\n" +
            "\t.size\tNELReduceAddI32, .-NELReduceAddI32\n" +
            "\t.align\t1\n" +
            "\t.globl\tNELReduceAddF32\n" +
            "\t.type\tNELReduceAddF32, @function\n" +
            "NELReduceAddF32:\n" +
            ".LFB1270:\n" +
            "\t.cfi_startproc\n" +
            "\taddi\tsp,sp,-16\n" +
            "\t.cfi_def_cfa_offset 16\n" +
            "\tfence\tiorw,iorw\n" +
            "\tlw\ta5,0(a0)\n" +
            "\tfence\tiorw,iorw\n" +
            "\tfmv.w.x\tfa5,a5\n" +
            "\taddi\ta2,sp,12\n" +
            "\tsw\ta5,12(sp)\n" +
            ".L131:\n" +
            "\tfadd.s\tfa5,fa0,fa5\n" +
            "\tlw\ta5,0(a2)\n" +
            "\tfmv.x.w\ta4,fa5\n" +
            "\tfence iorw,ow;  1: lr.w.aq a3,0(a0); bne a3,a5,1f; sc.w.aq a6,a4,0(a0); bnez a6,1b; 1:\n" +
            "\tsubw\ta5,a3,a5\n" +
            "\tseqz\ta4,a5\n" +
            "\tbeq\ta5,zero,.L129\n" +
            "\tsw\ta3,0(a2)\n" +
            ".L129:\n" +
            "\tzext.w\ta5,a4\n" +
            "\tbeq\ta5,zero,.L133\n" +
            "\taddi\tsp,sp,16\n" +
            "\t.cfi_remember_state\n" +
            "\t.cfi_def_cfa_offset 0\n" +
            "\tjr\tra\n" +
            ".L133:\n" +
            "\t.cfi_restore_state\n" +
            "\tflw\tfa5,12(sp)\n" +
            "\tj\t.L131\n" +
            "\t.cfi_endproc\n" +
            ".LFE1270:\n" +
            "\t.size\tNELReduceAddF32, .-NELReduceAddF32\n" +
            "\t.bss\n" +
            "\t.align\t3\n" +
            "\t.set\t.LANCHOR0,. + 0\n" +
            "\t.type\t_ZN12_GLOBAL__N_17workersE, @object\n" +
            "\t.size\t_ZN12_GLOBAL__N_17workersE, 192\n" +
            "_ZN12_GLOBAL__N_17workersE:\n" +
            "\t.zero\t192\n" +
            "\t.type\t_ZL13parallelCache, @object\n" +
            "\t.size\t_ZL13parallelCache, 896\n" +
            "_ZL13parallelCache:\n" +
            "\t.zero\t896\n" +
            "\t.type\t_ZL9lookupPtr, @object\n" +
            "\t.size\t_ZL9lookupPtr, 4\n" +
            "_ZL9lookupPtr:\n" +
            "\t.zero\t4\n" +
            "\t.ident\t\"GCC: (Ubuntu 12.3.0-1ubuntu1~22.04) 12.3.0\"\n" +
            "\t.section\t.note.GNU-stack,\"\",@progbits\n";
}

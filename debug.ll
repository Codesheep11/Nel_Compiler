@pos = global i32 0
@buffer = global [50000000 x i32] zeroinitializer
declare i32 @llvm.smax.i32(i32, i32)
declare i32 @llvm.smin.i32(i32, i32)
declare i32 @getch()
declare void @putint(i32)
declare void @putch(i32)
define i32 @detect_item(i32 %arg_0, i32* %arg_1, i32 %arg_2) {
detect_item_BB0:
	%load_20 = load i32, i32* @pos
	%icmp_7 = icmp sge i32 %load_20, %arg_2
	br i1 %icmp_7, label %detect_item_BB1, label %skip_space_BB1_cp0


detect_item_BB1:
	ret i32 0


detect_item_BB3:
	%load_25 = load i32, i32* @pos
	%gep_4 = getelementptr i32, i32* %arg_1, i32 %load_25
	%load_27 = load i32, i32* %gep_4
	%icmp_9 = icmp eq i32 %load_27, 123
	br i1 %icmp_9, label %detect_item_BB6, label %detect_item_BB7


detect_item_BB4:
	%icmp_18 = icmp eq i32 %arg_0, 1
	br i1 %icmp_18, label %detect_item_BB33, label %detect_item_BB34


detect_item_BB6:
	%call_1 = call i32 @detect_item(i32 4, i32* %arg_1, i32 %arg_2)
	ret i32 %call_1


detect_item_BB7:
	%load_30 = load i32, i32* @pos
	%gep_5 = getelementptr i32, i32* %arg_1, i32 %load_30
	%load_32 = load i32, i32* %gep_5
	%icmp_10 = icmp eq i32 %load_32, 91
	br i1 %icmp_10, label %detect_item_BB9, label %detect_item_BB10


detect_item_BB9:
	%call_2 = call i32 @detect_item(i32 3, i32* %arg_1, i32 %arg_2)
	ret i32 %call_2


detect_item_BB10:
	%load_35 = load i32, i32* @pos
	%gep_6 = getelementptr i32, i32* %arg_1, i32 %load_35
	%load_37 = load i32, i32* %gep_6
	%icmp_11 = icmp eq i32 %load_37, 34
	br i1 %icmp_11, label %detect_item_BB12, label %detect_item_BB13


detect_item_BB12:
	%call_3 = call i32 @detect_item(i32 2, i32* %arg_1, i32 %arg_2)
	ret i32 %call_3


detect_item_BB13:
	%load_40 = load i32, i32* @pos
	%gep_7 = getelementptr i32, i32* %arg_1, i32 %load_40
	%load_42 = load i32, i32* %gep_7
	%icmp_86 = icmp sge i32 %load_42, 48
	br i1 %icmp_86, label %is_number_BB1_cp0, label %is_number_BB2_cp0


detect_item_BB15:
	%call_5 = call i32 @detect_item(i32 1, i32* %arg_1, i32 %arg_2)
	ret i32 %call_5


detect_item_BB16:
	%load_45 = load i32, i32* @pos
	%gep_8 = getelementptr i32, i32* %arg_1, i32 %load_45
	%load_47 = load i32, i32* %gep_8
	%icmp_13 = icmp eq i32 %load_47, 43
	br i1 %icmp_13, label %detect_item_BB18, label %detect_item_BB19


detect_item_BB18:
	%call_6 = call i32 @detect_item(i32 1, i32* %arg_1, i32 %arg_2)
	ret i32 %call_6


detect_item_BB19:
	%load_50 = load i32, i32* @pos
	%gep_9 = getelementptr i32, i32* %arg_1, i32 %load_50
	%load_52 = load i32, i32* %gep_9
	%icmp_14 = icmp eq i32 %load_52, 45
	br i1 %icmp_14, label %detect_item_BB21, label %detect_item_BB22


detect_item_BB21:
	%call_7 = call i32 @detect_item(i32 1, i32* %arg_1, i32 %arg_2)
	ret i32 %call_7


detect_item_BB22:
	%load_55 = load i32, i32* @pos
	%gep_10 = getelementptr i32, i32* %arg_1, i32 %load_55
	%load_57 = load i32, i32* %gep_10
	%icmp_15 = icmp eq i32 %load_57, 116
	br i1 %icmp_15, label %detect_item_BB24, label %detect_item_BB25


detect_item_BB24:
	%call_8 = call i32 @detect_item(i32 5, i32* %arg_1, i32 %arg_2)
	ret i32 %call_8


detect_item_BB25:
	%load_60 = load i32, i32* @pos
	%gep_11 = getelementptr i32, i32* %arg_1, i32 %load_60
	%load_62 = load i32, i32* %gep_11
	%icmp_16 = icmp eq i32 %load_62, 102
	br i1 %icmp_16, label %detect_item_BB27, label %detect_item_BB28


detect_item_BB27:
	%call_9 = call i32 @detect_item(i32 6, i32* %arg_1, i32 %arg_2)
	ret i32 %call_9


detect_item_BB28:
	%load_65 = load i32, i32* @pos
	%gep_12 = getelementptr i32, i32* %arg_1, i32 %load_65
	%load_67 = load i32, i32* %gep_12
	%icmp_17 = icmp eq i32 %load_67, 110
	br i1 %icmp_17, label %detect_item_BB30, label %detect_item_BB31


detect_item_BB30:
	%call_10 = call i32 @detect_item(i32 7, i32* %arg_1, i32 %arg_2)
	ret i32 %call_10


detect_item_BB31:
	ret i32 0


detect_item_BB33:
	%load_71 = load i32, i32* @pos
	%gep_13 = getelementptr i32, i32* %arg_1, i32 %load_71
	%load_73 = load i32, i32* %gep_13
	%icmp_19 = icmp eq i32 %load_73, 43
	br i1 %icmp_19, label %detect_item_BB36, label %detect_item_BB37


detect_item_BB34:
	%icmp_37 = icmp eq i32 %arg_0, 2
	br i1 %icmp_37, label %detect_item_BB77, label %detect_item_BB78


detect_item_BB35:
	ret i32 1


detect_item_BB36:
	%load_74 = load i32, i32* @pos
	%add_162 = add i32 1, %load_74
	store i32 %add_162, i32* @pos
	br label %detect_item_BB38


detect_item_BB37:
	%load_75 = load i32, i32* @pos
	%gep_14 = getelementptr i32, i32* %arg_1, i32 %load_75
	%load_77 = load i32, i32* %gep_14
	%icmp_20 = icmp eq i32 %load_77, 45
	br i1 %icmp_20, label %detect_item_BB39, label %detect_item_BB38


detect_item_BB38:
	%load_79 = load i32, i32* @pos
	%icmp_21 = icmp sge i32 %load_79, %arg_2
	br i1 %icmp_21, label %detect_item_BB41, label %detect_item_BB42


detect_item_BB39:
	%load_78 = load i32, i32* @pos
	%add_165 = add i32 1, %load_78
	store i32 %add_165, i32* @pos
	br label %detect_item_BB38


detect_item_BB41:
	ret i32 0


detect_item_BB42:
	%load_81 = load i32, i32* @pos
	%gep_15 = getelementptr i32, i32* %arg_1, i32 %load_81
	%load_83 = load i32, i32* %gep_15
	%icmp_89 = icmp sge i32 %load_83, 48
	br i1 %icmp_89, label %is_number_BB1_cp1, label %is_number_BB2_cp1


detect_item_BB44:
	ret i32 0


detect_item_BB46:
	%load_84 = load i32, i32* @pos
	%icmp_23 = icmp slt i32 %load_84, %arg_2
	br i1 %icmp_23, label %detect_item_BB47, label %detect_item_BB48


detect_item_BB47:
	%load_86 = load i32, i32* @pos
	%gep_16 = getelementptr i32, i32* %arg_1, i32 %load_86
	%load_88 = load i32, i32* %gep_16
	%icmp_92 = icmp sge i32 %load_88, 48
	br i1 %icmp_92, label %is_number_BB1_cp2, label %is_number_BB2_cp2


detect_item_BB48:
	%load_90 = load i32, i32* @pos
	%icmp_25 = icmp slt i32 %load_90, %arg_2
	br i1 %icmp_25, label %detect_item_BB51, label %detect_item_BB52


detect_item_BB50:
	%load_89 = load i32, i32* @pos
	%add_180 = add i32 1, %load_89
	store i32 %add_180, i32* @pos
	br label %detect_item_BB46


detect_item_BB51:
	%load_92 = load i32, i32* @pos
	%gep_17 = getelementptr i32, i32* %arg_1, i32 %load_92
	%load_94 = load i32, i32* %gep_17
	%icmp_26 = icmp eq i32 %load_94, 46
	br i1 %icmp_26, label %detect_item_BB53, label %detect_item_BB52


detect_item_BB52:
	%load_102 = load i32, i32* @pos
	%icmp_29 = icmp slt i32 %load_102, %arg_2
	br i1 %icmp_29, label %detect_item_BB60, label %detect_item_BB35


detect_item_BB53:
	%load_95 = load i32, i32* @pos
	%add_181 = add i32 1, %load_95
	store i32 %add_181, i32* @pos
	br label %detect_item_BB55


detect_item_BB55:
	%load_96 = load i32, i32* @pos
	%icmp_27 = icmp slt i32 %load_96, %arg_2
	br i1 %icmp_27, label %detect_item_BB56, label %detect_item_BB52


detect_item_BB56:
	%load_98 = load i32, i32* @pos
	%gep_18 = getelementptr i32, i32* %arg_1, i32 %load_98
	%load_100 = load i32, i32* %gep_18
	%icmp_95 = icmp sge i32 %load_100, 48
	br i1 %icmp_95, label %is_number_BB1_cp3, label %is_number_BB2_cp3


detect_item_BB59:
	%load_101 = load i32, i32* @pos
	%add_208 = add i32 1, %load_101
	store i32 %add_208, i32* @pos
	br label %detect_item_BB55


detect_item_BB60:
	%load_104 = load i32, i32* @pos
	%gep_19 = getelementptr i32, i32* %arg_1, i32 %load_104
	%load_106 = load i32, i32* %gep_19
	%icmp_30 = icmp eq i32 %load_106, 101
	br i1 %icmp_30, label %detect_item_BB62, label %detect_item_BB35


detect_item_BB62:
	%load_107 = load i32, i32* @pos
	%add_187 = add i32 1, %load_107
	store i32 %add_187, i32* @pos
	%icmp_31 = icmp slt i32 %add_187, %arg_2
	br i1 %icmp_31, label %detect_item_BB64, label %detect_item_BB65


detect_item_BB64:
	%load_110 = load i32, i32* @pos
	%gep_20 = getelementptr i32, i32* %arg_1, i32 %load_110
	%load_112 = load i32, i32* %gep_20
	%icmp_32 = icmp eq i32 %load_112, 43
	br i1 %icmp_32, label %detect_item_BB66, label %detect_item_BB65


detect_item_BB65:
	%load_114 = load i32, i32* @pos
	%icmp_33 = icmp slt i32 %load_114, %arg_2
	br i1 %icmp_33, label %detect_item_BB68, label %detect_item_BB72


detect_item_BB66:
	%load_113 = load i32, i32* @pos
	%add_201 = add i32 1, %load_113
	store i32 %add_201, i32* @pos
	br label %detect_item_BB65


detect_item_BB68:
	%load_116 = load i32, i32* @pos
	%gep_21 = getelementptr i32, i32* %arg_1, i32 %load_116
	%load_118 = load i32, i32* %gep_21
	%icmp_34 = icmp eq i32 %load_118, 45
	br i1 %icmp_34, label %detect_item_BB70, label %detect_item_BB72


detect_item_BB70:
	%load_119 = load i32, i32* @pos
	%add_209 = add i32 1, %load_119
	store i32 %add_209, i32* @pos
	br label %detect_item_BB72


detect_item_BB72:
	%load_120 = load i32, i32* @pos
	%icmp_35 = icmp slt i32 %load_120, %arg_2
	br i1 %icmp_35, label %detect_item_BB73, label %detect_item_BB35


detect_item_BB73:
	%load_122 = load i32, i32* @pos
	%gep_22 = getelementptr i32, i32* %arg_1, i32 %load_122
	%load_124 = load i32, i32* %gep_22
	%icmp_98 = icmp sge i32 %load_124, 48
	br i1 %icmp_98, label %is_number_BB1_cp4, label %is_number_BB2_cp4


detect_item_BB76:
	%load_125 = load i32, i32* @pos
	%add_220 = add i32 1, %load_125
	store i32 %add_220, i32* @pos
	br label %detect_item_BB72


detect_item_BB77:
	%load_127 = load i32, i32* @pos
	%add_163 = add i32 1, %load_127
	store i32 %add_163, i32* @pos
	br label %detect_item_BB80


detect_item_BB78:
	%icmp_43 = icmp eq i32 %arg_0, 3
	br i1 %icmp_43, label %detect_item_BB93, label %detect_item_BB94


detect_item_BB80:
	%load_128 = load i32, i32* @pos
	%icmp_38 = icmp slt i32 %load_128, %arg_2
	br i1 %icmp_38, label %detect_item_BB81, label %detect_item_BB82


detect_item_BB81:
	%load_130 = load i32, i32* @pos
	%gep_23 = getelementptr i32, i32* %arg_1, i32 %load_130
	%load_132 = load i32, i32* %gep_23
	%icmp_39 = icmp eq i32 %load_132, 34
	br i1 %icmp_39, label %detect_item_BB82, label %detect_item_BB84


detect_item_BB82:
	%load_138 = load i32, i32* @pos
	%icmp_41 = icmp sge i32 %load_138, %arg_2
	br i1 %icmp_41, label %detect_item_BB88, label %detect_item_BB89


detect_item_BB84:
	%load_133 = load i32, i32* @pos
	%gep_24 = getelementptr i32, i32* %arg_1, i32 %load_133
	%load_135 = load i32, i32* %gep_24
	%icmp_40 = icmp eq i32 %load_135, 92
	br i1 %icmp_40, label %detect_item_BB85, label %detect_item_BB86


detect_item_BB85:
	%load_136 = load i32, i32* @pos
	%add_170 = add i32 2, %load_136
	store i32 %add_170, i32* @pos
	br label %detect_item_BB80


detect_item_BB86:
	%load_137 = load i32, i32* @pos
	%add_171 = add i32 1, %load_137
	store i32 %add_171, i32* @pos
	br label %detect_item_BB80


detect_item_BB88:
	ret i32 0


detect_item_BB89:
	%load_140 = load i32, i32* @pos
	%gep_25 = getelementptr i32, i32* %arg_1, i32 %load_140
	%load_142 = load i32, i32* %gep_25
	%icmp_42 = icmp ne i32 %load_142, 34
	br i1 %icmp_42, label %detect_item_BB91, label %detect_item_BB92


detect_item_BB91:
	ret i32 0


detect_item_BB92:
	%load_143 = load i32, i32* @pos
	%add_172 = add i32 1, %load_143
	store i32 %add_172, i32* @pos
	br label %detect_item_BB35


detect_item_BB93:
	%load_145 = load i32, i32* @pos
	%add_166 = add i32 1, %load_145
	store i32 %add_166, i32* @pos
	br label %skip_space_BB1_cp1


detect_item_BB94:
	%icmp_51 = icmp eq i32 %arg_0, 4
	br i1 %icmp_51, label %detect_item_BB111, label %detect_item_BB112


detect_item_BB96:
	%load_150 = load i32, i32* @pos
	%gep_26 = getelementptr i32, i32* %arg_1, i32 %load_150
	%load_152 = load i32, i32* %gep_26
	%icmp_45 = icmp eq i32 %load_152, 93
	br i1 %icmp_45, label %detect_item_BB98, label %detect_item_BB97


detect_item_BB97:
	%call_16 = call i32 @detect_item(i32 0, i32* %arg_1, i32 %arg_2)
	%icmp_46 = icmp eq i32 %call_16, 0
	br i1 %icmp_46, label %detect_item_BB100, label %skip_space_BB1_cp2


detect_item_BB98:
	%load_153 = load i32, i32* @pos
	%add_175 = add i32 1, %load_153
	store i32 %add_175, i32* @pos
	ret i32 1


detect_item_BB100:
	ret i32 0


detect_item_BB102:
	%load_158 = load i32, i32* @pos
	%gep_27 = getelementptr i32, i32* %arg_1, i32 %load_158
	%load_160 = load i32, i32* %gep_27
	%icmp_47 = icmp eq i32 %load_160, 44
	br i1 %icmp_47, label %detect_item_BB103, label %skip_space_BB1_cp5


detect_item_BB103:
	%load_161 = load i32, i32* @pos
	%add_188 = add i32 1, %load_161
	store i32 %add_188, i32* @pos
	br label %skip_space_BB1_cp3


detect_item_BB105:
	ret i32 0


detect_item_BB107:
	ret i32 0


detect_item_BB108:
	%load_172 = load i32, i32* @pos
	%gep_28 = getelementptr i32, i32* %arg_1, i32 %load_172
	%load_174 = load i32, i32* %gep_28
	%icmp_50 = icmp ne i32 %load_174, 93
	br i1 %icmp_50, label %detect_item_BB109, label %detect_item_BB110


detect_item_BB109:
	ret i32 0


detect_item_BB110:
	%load_175 = load i32, i32* @pos
	%add_211 = add i32 1, %load_175
	store i32 %add_211, i32* @pos
	br label %detect_item_BB35


detect_item_BB111:
	%load_177 = load i32, i32* @pos
	%add_168 = add i32 1, %load_177
	store i32 %add_168, i32* @pos
	br label %skip_space_BB1_cp6


detect_item_BB112:
	%icmp_65 = icmp eq i32 %arg_0, 5
	br i1 %icmp_65, label %detect_item_BB142, label %detect_item_BB143


detect_item_BB114:
	%load_182 = load i32, i32* @pos
	%gep_29 = getelementptr i32, i32* %arg_1, i32 %load_182
	%load_184 = load i32, i32* %gep_29
	%icmp_53 = icmp eq i32 %load_184, 125
	br i1 %icmp_53, label %detect_item_BB116, label %detect_item_BB115


detect_item_BB115:
	%call_23 = call i32 @detect_item(i32 2, i32* %arg_1, i32 %arg_2)
	%icmp_54 = icmp eq i32 %call_23, 0
	br i1 %icmp_54, label %detect_item_BB118, label %skip_space_BB1_cp7


detect_item_BB116:
	%load_185 = load i32, i32* @pos
	%add_183 = add i32 1, %load_185
	store i32 %add_183, i32* @pos
	ret i32 1


detect_item_BB118:
	ret i32 0


detect_item_BB120:
	ret i32 0


detect_item_BB121:
	%load_192 = load i32, i32* @pos
	%gep_30 = getelementptr i32, i32* %arg_1, i32 %load_192
	%load_194 = load i32, i32* %gep_30
	%icmp_56 = icmp ne i32 %load_194, 58
	br i1 %icmp_56, label %detect_item_BB122, label %detect_item_BB123


detect_item_BB122:
	ret i32 0


detect_item_BB123:
	%load_195 = load i32, i32* @pos
	%add_204 = add i32 1, %load_195
	store i32 %add_204, i32* @pos
	br label %skip_space_BB1_cp8


detect_item_BB124:
	ret i32 0


detect_item_BB126:
	%load_202 = load i32, i32* @pos
	%gep_31 = getelementptr i32, i32* %arg_1, i32 %load_202
	%load_204 = load i32, i32* %gep_31
	%icmp_58 = icmp eq i32 %load_204, 44
	br i1 %icmp_58, label %detect_item_BB127, label %skip_space_BB1_cp14


detect_item_BB127:
	%load_205 = load i32, i32* @pos
	%add_229 = add i32 1, %load_205
	store i32 %add_229, i32* @pos
	br label %skip_space_BB1_cp10


detect_item_BB129:
	ret i32 0


detect_item_BB131:
	ret i32 0


detect_item_BB132:
	%load_214 = load i32, i32* @pos
	%gep_32 = getelementptr i32, i32* %arg_1, i32 %load_214
	%load_216 = load i32, i32* %gep_32
	%icmp_61 = icmp ne i32 %load_216, 58
	br i1 %icmp_61, label %detect_item_BB133, label %detect_item_BB134


detect_item_BB133:
	ret i32 0


detect_item_BB134:
	%load_217 = load i32, i32* @pos
	%add_246 = add i32 1, %load_217
	store i32 %add_246, i32* @pos
	br label %skip_space_BB1_cp12


detect_item_BB135:
	ret i32 0


detect_item_BB137:
	ret i32 0


detect_item_BB138:
	%load_228 = load i32, i32* @pos
	%gep_33 = getelementptr i32, i32* %arg_1, i32 %load_228
	%load_230 = load i32, i32* %gep_33
	%icmp_64 = icmp ne i32 %load_230, 125
	br i1 %icmp_64, label %detect_item_BB140, label %detect_item_BB141


detect_item_BB140:
	ret i32 0


detect_item_BB141:
	%load_231 = load i32, i32* @pos
	%add_238 = add i32 1, %load_231
	store i32 %add_238, i32* @pos
	br label %detect_item_BB35


detect_item_BB142:
	%load_233 = load i32, i32* @pos
	%add_169 = add i32 3, %load_233
	%icmp_66 = icmp sge i32 %add_169, %arg_2
	br i1 %icmp_66, label %detect_item_BB145, label %detect_item_BB146


detect_item_BB143:
	%icmp_71 = icmp eq i32 %arg_0, 6
	br i1 %icmp_71, label %detect_item_BB159, label %detect_item_BB160


detect_item_BB145:
	ret i32 0


detect_item_BB146:
	%load_235 = load i32, i32* @pos
	%gep_38 = getelementptr i32, i32* %arg_1, i32 %load_235
	%load_237 = load i32, i32* %gep_38
	%icmp_67 = icmp ne i32 %load_237, 116
	br i1 %icmp_67, label %detect_item_BB148, label %detect_item_BB149


detect_item_BB148:
	ret i32 0


detect_item_BB149:
	%load_239 = load i32, i32* @pos
	%add_178 = add i32 1, %load_239
	%gep_40 = getelementptr i32, i32* %arg_1, i32 %add_178
	%load_241 = load i32, i32* %gep_40
	%icmp_68 = icmp ne i32 %load_241, 114
	br i1 %icmp_68, label %detect_item_BB151, label %detect_item_BB152


detect_item_BB151:
	ret i32 0


detect_item_BB152:
	%load_243 = load i32, i32* @pos
	%add_185 = add i32 2, %load_243
	%gep_42 = getelementptr i32, i32* %arg_1, i32 %add_185
	%load_245 = load i32, i32* %gep_42
	%icmp_69 = icmp ne i32 %load_245, 117
	br i1 %icmp_69, label %detect_item_BB154, label %detect_item_BB155


detect_item_BB154:
	ret i32 0


detect_item_BB155:
	%load_247 = load i32, i32* @pos
	%add_192 = add i32 3, %load_247
	%gep_44 = getelementptr i32, i32* %arg_1, i32 %add_192
	%load_249 = load i32, i32* %gep_44
	%icmp_70 = icmp ne i32 %load_249, 101
	br i1 %icmp_70, label %detect_item_BB157, label %detect_item_BB158


detect_item_BB157:
	ret i32 0


detect_item_BB158:
	%load_251 = load i32, i32* @pos
	%add_198 = add i32 4, %load_251
	store i32 %add_198, i32* @pos
	br label %detect_item_BB35


detect_item_BB159:
	%load_253 = load i32, i32* @pos
	%add_174 = add i32 4, %load_253
	%icmp_72 = icmp sge i32 %add_174, %arg_2
	br i1 %icmp_72, label %detect_item_BB162, label %detect_item_BB163


detect_item_BB160:
	%icmp_78 = icmp eq i32 %arg_0, 7
	br i1 %icmp_78, label %detect_item_BB179, label %detect_item_BB180


detect_item_BB162:
	ret i32 0


detect_item_BB163:
	%load_255 = load i32, i32* @pos
	%gep_51 = getelementptr i32, i32* %arg_1, i32 %load_255
	%load_257 = load i32, i32* %gep_51
	%icmp_73 = icmp ne i32 %load_257, 102
	br i1 %icmp_73, label %detect_item_BB165, label %detect_item_BB166


detect_item_BB165:
	ret i32 0


detect_item_BB166:
	%load_259 = load i32, i32* @pos
	%add_186 = add i32 1, %load_259
	%gep_53 = getelementptr i32, i32* %arg_1, i32 %add_186
	%load_261 = load i32, i32* %gep_53
	%icmp_74 = icmp ne i32 %load_261, 97
	br i1 %icmp_74, label %detect_item_BB168, label %detect_item_BB169


detect_item_BB168:
	ret i32 0


detect_item_BB169:
	%load_263 = load i32, i32* @pos
	%add_193 = add i32 2, %load_263
	%gep_55 = getelementptr i32, i32* %arg_1, i32 %add_193
	%load_265 = load i32, i32* %gep_55
	%icmp_75 = icmp ne i32 %load_265, 108
	br i1 %icmp_75, label %detect_item_BB171, label %detect_item_BB172


detect_item_BB171:
	ret i32 0


detect_item_BB172:
	%load_267 = load i32, i32* @pos
	%add_199 = add i32 3, %load_267
	%gep_57 = getelementptr i32, i32* %arg_1, i32 %add_199
	%load_269 = load i32, i32* %gep_57
	%icmp_76 = icmp ne i32 %load_269, 115
	br i1 %icmp_76, label %detect_item_BB174, label %detect_item_BB175


detect_item_BB174:
	ret i32 0


detect_item_BB175:
	%load_271 = load i32, i32* @pos
	%add_206 = add i32 4, %load_271
	%gep_59 = getelementptr i32, i32* %arg_1, i32 %add_206
	%load_273 = load i32, i32* %gep_59
	%icmp_77 = icmp ne i32 %load_273, 101
	br i1 %icmp_77, label %detect_item_BB177, label %detect_item_BB178


detect_item_BB177:
	ret i32 0


detect_item_BB178:
	%load_275 = load i32, i32* @pos
	%add_215 = add i32 5, %load_275
	store i32 %add_215, i32* @pos
	br label %detect_item_BB35


detect_item_BB179:
	%load_277 = load i32, i32* @pos
	%add_179 = add i32 3, %load_277
	%icmp_79 = icmp sge i32 %add_179, %arg_2
	br i1 %icmp_79, label %detect_item_BB182, label %detect_item_BB183


detect_item_BB180:
	ret i32 0


detect_item_BB182:
	ret i32 0


detect_item_BB183:
	%load_279 = load i32, i32* @pos
	%gep_65 = getelementptr i32, i32* %arg_1, i32 %load_279
	%load_281 = load i32, i32* %gep_65
	%icmp_80 = icmp ne i32 %load_281, 110
	br i1 %icmp_80, label %detect_item_BB185, label %detect_item_BB186


detect_item_BB185:
	ret i32 0


detect_item_BB186:
	%load_283 = load i32, i32* @pos
	%add_194 = add i32 1, %load_283
	%gep_67 = getelementptr i32, i32* %arg_1, i32 %add_194
	%load_285 = load i32, i32* %gep_67
	%icmp_81 = icmp ne i32 %load_285, 117
	br i1 %icmp_81, label %detect_item_BB188, label %detect_item_BB189


detect_item_BB188:
	ret i32 0


detect_item_BB189:
	%load_287 = load i32, i32* @pos
	%add_200 = add i32 2, %load_287
	%gep_69 = getelementptr i32, i32* %arg_1, i32 %add_200
	%load_289 = load i32, i32* %gep_69
	%icmp_82 = icmp ne i32 %load_289, 108
	br i1 %icmp_82, label %detect_item_BB191, label %detect_item_BB192


detect_item_BB191:
	ret i32 0


detect_item_BB192:
	%load_291 = load i32, i32* @pos
	%add_207 = add i32 3, %load_291
	%gep_71 = getelementptr i32, i32* %arg_1, i32 %add_207
	%load_293 = load i32, i32* %gep_71
	%icmp_83 = icmp ne i32 %load_293, 108
	br i1 %icmp_83, label %detect_item_BB194, label %detect_item_BB195


detect_item_BB194:
	ret i32 0


detect_item_BB195:
	%load_295 = load i32, i32* @pos
	%add_216 = add i32 4, %load_295
	store i32 %add_216, i32* @pos
	br label %detect_item_BB35


is_number_ret_0:
	%icmp_88 = icmp eq i32 %phi_2, 1
	br i1 %icmp_88, label %detect_item_BB15, label %detect_item_BB16


is_number_BB1_cp0:
	%icmp_87 = icmp sle i32 %load_42, 57
	br i1 %icmp_87, label %is_number_BB4_cp0, label %is_number_BB5_cp0


is_number_BB2_cp0:
	move %phi_2 <-- 0
	br label %is_number_ret_0


is_number_BB4_cp0:
	move %phi_2 <-- 1
	br label %is_number_ret_0


is_number_BB5_cp0:
	move %phi_2 <-- 0
	br label %is_number_ret_0


is_number_ret_1:
	%icmp_91 = icmp eq i32 %phi_3, 0
	br i1 %icmp_91, label %detect_item_BB44, label %detect_item_BB46


is_number_BB1_cp1:
	%icmp_90 = icmp sle i32 %load_83, 57
	br i1 %icmp_90, label %is_number_BB4_cp1, label %is_number_BB5_cp1


is_number_BB2_cp1:
	move %phi_3 <-- 0
	br label %is_number_ret_1


is_number_BB4_cp1:
	move %phi_3 <-- 1
	br label %is_number_ret_1


is_number_BB5_cp1:
	move %phi_3 <-- 0
	br label %is_number_ret_1


is_number_ret_2:
	%icmp_94 = icmp ne i32 %phi_4, 1
	br i1 %icmp_94, label %detect_item_BB48, label %detect_item_BB50


is_number_BB1_cp2:
	%icmp_93 = icmp sle i32 %load_88, 57
	br i1 %icmp_93, label %is_number_BB4_cp2, label %is_number_BB5_cp2


is_number_BB2_cp2:
	move %phi_4 <-- 0
	br label %is_number_ret_2


is_number_BB4_cp2:
	move %phi_4 <-- 1
	br label %is_number_ret_2


is_number_BB5_cp2:
	move %phi_4 <-- 0
	br label %is_number_ret_2


is_number_ret_3:
	%icmp_97 = icmp ne i32 %phi_5, 1
	br i1 %icmp_97, label %detect_item_BB52, label %detect_item_BB59


is_number_BB1_cp3:
	%icmp_96 = icmp sle i32 %load_100, 57
	br i1 %icmp_96, label %is_number_BB4_cp3, label %is_number_BB5_cp3


is_number_BB2_cp3:
	move %phi_5 <-- 0
	br label %is_number_ret_3


is_number_BB4_cp3:
	move %phi_5 <-- 1
	br label %is_number_ret_3


is_number_BB5_cp3:
	move %phi_5 <-- 0
	br label %is_number_ret_3


is_number_ret_4:
	%icmp_100 = icmp ne i32 %phi_6, 1
	br i1 %icmp_100, label %detect_item_BB35, label %detect_item_BB76


is_number_BB1_cp4:
	%icmp_99 = icmp sle i32 %load_124, 57
	br i1 %icmp_99, label %is_number_BB4_cp4, label %is_number_BB5_cp4


is_number_BB2_cp4:
	move %phi_6 <-- 0
	br label %is_number_ret_4


is_number_BB4_cp4:
	move %phi_6 <-- 1
	br label %is_number_ret_4


is_number_BB5_cp4:
	move %phi_6 <-- 0
	br label %is_number_ret_4


skip_space_BB1_cp0:
	%load_304 = load i32, i32* @pos
	%icmp_101 = icmp sge i32 %load_304, %arg_2
	br i1 %icmp_101, label %skip_space_BB3_cp0, label %skip_space_BB5_cp0


skip_space_BB3_cp0:
	%icmp_106 = icmp eq i32 %arg_0, 0
	br i1 %icmp_106, label %detect_item_BB3, label %detect_item_BB4


skip_space_BB5_cp0:
	%load_305 = load i32, i32* @pos
	%gep_77 = getelementptr i32, i32* %arg_1, i32 %load_305
	%load_306 = load i32, i32* %gep_77
	%icmp_102 = icmp eq i32 %load_306, 32
	br i1 %icmp_102, label %skip_space_BB6_cp0, label %skip_space_BB7_cp0


skip_space_BB6_cp0:
	%load_307 = load i32, i32* @pos
	%add_160 = add i32 1, %load_307
	store i32 %add_160, i32* @pos
	br label %skip_space_BB1_cp0


skip_space_BB7_cp0:
	%load_308 = load i32, i32* @pos
	%gep_78 = getelementptr i32, i32* %arg_1, i32 %load_308
	%load_309 = load i32, i32* %gep_78
	%icmp_103 = icmp eq i32 %load_309, 9
	br i1 %icmp_103, label %skip_space_BB9_cp0, label %skip_space_BB10_cp0


skip_space_BB9_cp0:
	%load_310 = load i32, i32* @pos
	%add_161 = add i32 1, %load_310
	store i32 %add_161, i32* @pos
	br label %skip_space_BB1_cp0


skip_space_BB10_cp0:
	%load_311 = load i32, i32* @pos
	%gep_79 = getelementptr i32, i32* %arg_1, i32 %load_311
	%load_312 = load i32, i32* %gep_79
	%icmp_104 = icmp eq i32 %load_312, 10
	br i1 %icmp_104, label %skip_space_BB12_cp0, label %skip_space_BB13_cp0


skip_space_BB12_cp0:
	%load_313 = load i32, i32* @pos
	%add_164 = add i32 1, %load_313
	store i32 %add_164, i32* @pos
	br label %skip_space_BB1_cp0


skip_space_BB13_cp0:
	%load_314 = load i32, i32* @pos
	%gep_80 = getelementptr i32, i32* %arg_1, i32 %load_314
	%load_315 = load i32, i32* %gep_80
	%icmp_105 = icmp eq i32 %load_315, 13
	br i1 %icmp_105, label %skip_space_BB15_cp0, label %skip_space_BB3_cp0


skip_space_BB15_cp0:
	%load_316 = load i32, i32* @pos
	%add_167 = add i32 1, %load_316
	store i32 %add_167, i32* @pos
	br label %skip_space_BB1_cp0


skip_space_BB1_cp1:
	%load_317 = load i32, i32* @pos
	%icmp_107 = icmp sge i32 %load_317, %arg_2
	br i1 %icmp_107, label %skip_space_BB3_cp1, label %skip_space_BB5_cp1


skip_space_BB3_cp1:
	%load_330 = load i32, i32* @pos
	%icmp_112 = icmp slt i32 %load_330, %arg_2
	br i1 %icmp_112, label %detect_item_BB96, label %detect_item_BB97


skip_space_BB5_cp1:
	%load_318 = load i32, i32* @pos
	%gep_81 = getelementptr i32, i32* %arg_1, i32 %load_318
	%load_319 = load i32, i32* %gep_81
	%icmp_108 = icmp eq i32 %load_319, 32
	br i1 %icmp_108, label %skip_space_BB6_cp1, label %skip_space_BB7_cp1


skip_space_BB6_cp1:
	%load_320 = load i32, i32* @pos
	%add_173 = add i32 1, %load_320
	store i32 %add_173, i32* @pos
	br label %skip_space_BB1_cp1


skip_space_BB7_cp1:
	%load_321 = load i32, i32* @pos
	%gep_82 = getelementptr i32, i32* %arg_1, i32 %load_321
	%load_322 = load i32, i32* %gep_82
	%icmp_109 = icmp eq i32 %load_322, 9
	br i1 %icmp_109, label %skip_space_BB9_cp1, label %skip_space_BB10_cp1


skip_space_BB9_cp1:
	%load_323 = load i32, i32* @pos
	%add_176 = add i32 1, %load_323
	store i32 %add_176, i32* @pos
	br label %skip_space_BB1_cp1


skip_space_BB10_cp1:
	%load_324 = load i32, i32* @pos
	%gep_83 = getelementptr i32, i32* %arg_1, i32 %load_324
	%load_325 = load i32, i32* %gep_83
	%icmp_110 = icmp eq i32 %load_325, 10
	br i1 %icmp_110, label %skip_space_BB12_cp1, label %skip_space_BB13_cp1


skip_space_BB12_cp1:
	%load_326 = load i32, i32* @pos
	%add_182 = add i32 1, %load_326
	store i32 %add_182, i32* @pos
	br label %skip_space_BB1_cp1


skip_space_BB13_cp1:
	%load_327 = load i32, i32* @pos
	%gep_84 = getelementptr i32, i32* %arg_1, i32 %load_327
	%load_328 = load i32, i32* %gep_84
	%icmp_111 = icmp eq i32 %load_328, 13
	br i1 %icmp_111, label %skip_space_BB15_cp1, label %skip_space_BB3_cp1


skip_space_BB15_cp1:
	%load_329 = load i32, i32* @pos
	%add_190 = add i32 1, %load_329
	store i32 %add_190, i32* @pos
	br label %skip_space_BB1_cp1


skip_space_BB1_cp2:
	%load_331 = load i32, i32* @pos
	%icmp_113 = icmp sge i32 %load_331, %arg_2
	br i1 %icmp_113, label %detect_item_BB102, label %skip_space_BB5_cp2


skip_space_BB5_cp2:
	%load_332 = load i32, i32* @pos
	%gep_85 = getelementptr i32, i32* %arg_1, i32 %load_332
	%load_333 = load i32, i32* %gep_85
	%icmp_114 = icmp eq i32 %load_333, 32
	br i1 %icmp_114, label %skip_space_BB6_cp2, label %skip_space_BB7_cp2


skip_space_BB6_cp2:
	%load_334 = load i32, i32* @pos
	%add_189 = add i32 1, %load_334
	store i32 %add_189, i32* @pos
	br label %skip_space_BB1_cp2


skip_space_BB7_cp2:
	%load_335 = load i32, i32* @pos
	%gep_86 = getelementptr i32, i32* %arg_1, i32 %load_335
	%load_336 = load i32, i32* %gep_86
	%icmp_115 = icmp eq i32 %load_336, 9
	br i1 %icmp_115, label %skip_space_BB9_cp2, label %skip_space_BB10_cp2


skip_space_BB9_cp2:
	%load_337 = load i32, i32* @pos
	%add_195 = add i32 1, %load_337
	store i32 %add_195, i32* @pos
	br label %skip_space_BB1_cp2


skip_space_BB10_cp2:
	%load_338 = load i32, i32* @pos
	%gep_87 = getelementptr i32, i32* %arg_1, i32 %load_338
	%load_339 = load i32, i32* %gep_87
	%icmp_116 = icmp eq i32 %load_339, 10
	br i1 %icmp_116, label %skip_space_BB12_cp2, label %skip_space_BB13_cp2


skip_space_BB12_cp2:
	%load_340 = load i32, i32* @pos
	%add_203 = add i32 1, %load_340
	store i32 %add_203, i32* @pos
	br label %skip_space_BB1_cp2


skip_space_BB13_cp2:
	%load_341 = load i32, i32* @pos
	%gep_88 = getelementptr i32, i32* %arg_1, i32 %load_341
	%load_342 = load i32, i32* %gep_88
	%icmp_117 = icmp eq i32 %load_342, 13
	br i1 %icmp_117, label %skip_space_BB15_cp2, label %detect_item_BB102


skip_space_BB15_cp2:
	%load_343 = load i32, i32* @pos
	%add_213 = add i32 1, %load_343
	store i32 %add_213, i32* @pos
	br label %skip_space_BB1_cp2


skip_space_BB1_cp3:
	%load_344 = load i32, i32* @pos
	%icmp_118 = icmp sge i32 %load_344, %arg_2
	br i1 %icmp_118, label %skip_space_BB3_cp3, label %skip_space_BB5_cp3


skip_space_BB3_cp3:
	%call_54 = call i32 @detect_item(i32 0, i32* %arg_1, i32 %arg_2)
	%icmp_123 = icmp eq i32 %call_54, 0
	br i1 %icmp_123, label %detect_item_BB105, label %skip_space_BB1_cp4


skip_space_BB5_cp3:
	%load_345 = load i32, i32* @pos
	%gep_89 = getelementptr i32, i32* %arg_1, i32 %load_345
	%load_346 = load i32, i32* %gep_89
	%icmp_119 = icmp eq i32 %load_346, 32
	br i1 %icmp_119, label %skip_space_BB6_cp3, label %skip_space_BB7_cp3


skip_space_BB6_cp3:
	%load_347 = load i32, i32* @pos
	%add_210 = add i32 1, %load_347
	store i32 %add_210, i32* @pos
	br label %skip_space_BB1_cp3


skip_space_BB7_cp3:
	%load_348 = load i32, i32* @pos
	%gep_90 = getelementptr i32, i32* %arg_1, i32 %load_348
	%load_349 = load i32, i32* %gep_90
	%icmp_120 = icmp eq i32 %load_349, 9
	br i1 %icmp_120, label %skip_space_BB9_cp3, label %skip_space_BB10_cp3


skip_space_BB9_cp3:
	%load_350 = load i32, i32* @pos
	%add_217 = add i32 1, %load_350
	store i32 %add_217, i32* @pos
	br label %skip_space_BB1_cp3


skip_space_BB10_cp3:
	%load_351 = load i32, i32* @pos
	%gep_91 = getelementptr i32, i32* %arg_1, i32 %load_351
	%load_352 = load i32, i32* %gep_91
	%icmp_121 = icmp eq i32 %load_352, 10
	br i1 %icmp_121, label %skip_space_BB12_cp3, label %skip_space_BB13_cp3


skip_space_BB12_cp3:
	%load_353 = load i32, i32* @pos
	%add_222 = add i32 1, %load_353
	store i32 %add_222, i32* @pos
	br label %skip_space_BB1_cp3


skip_space_BB13_cp3:
	%load_354 = load i32, i32* @pos
	%gep_92 = getelementptr i32, i32* %arg_1, i32 %load_354
	%load_355 = load i32, i32* %gep_92
	%icmp_122 = icmp eq i32 %load_355, 13
	br i1 %icmp_122, label %skip_space_BB15_cp3, label %skip_space_BB3_cp3


skip_space_BB15_cp3:
	%load_356 = load i32, i32* @pos
	%add_226 = add i32 1, %load_356
	store i32 %add_226, i32* @pos
	br label %skip_space_BB1_cp3


skip_space_BB1_cp4:
	%load_357 = load i32, i32* @pos
	%icmp_124 = icmp sge i32 %load_357, %arg_2
	br i1 %icmp_124, label %detect_item_BB102, label %skip_space_BB5_cp4


skip_space_BB5_cp4:
	%load_358 = load i32, i32* @pos
	%gep_93 = getelementptr i32, i32* %arg_1, i32 %load_358
	%load_359 = load i32, i32* %gep_93
	%icmp_125 = icmp eq i32 %load_359, 32
	br i1 %icmp_125, label %skip_space_BB6_cp4, label %skip_space_BB7_cp4


skip_space_BB6_cp4:
	%load_360 = load i32, i32* @pos
	%add_221 = add i32 1, %load_360
	store i32 %add_221, i32* @pos
	br label %skip_space_BB1_cp4


skip_space_BB7_cp4:
	%load_361 = load i32, i32* @pos
	%gep_94 = getelementptr i32, i32* %arg_1, i32 %load_361
	%load_362 = load i32, i32* %gep_94
	%icmp_126 = icmp eq i32 %load_362, 9
	br i1 %icmp_126, label %skip_space_BB9_cp4, label %skip_space_BB10_cp4


skip_space_BB9_cp4:
	%load_363 = load i32, i32* @pos
	%add_225 = add i32 1, %load_363
	store i32 %add_225, i32* @pos
	br label %skip_space_BB1_cp4


skip_space_BB10_cp4:
	%load_364 = load i32, i32* @pos
	%gep_95 = getelementptr i32, i32* %arg_1, i32 %load_364
	%load_365 = load i32, i32* %gep_95
	%icmp_127 = icmp eq i32 %load_365, 10
	br i1 %icmp_127, label %skip_space_BB12_cp4, label %skip_space_BB13_cp4


skip_space_BB12_cp4:
	%load_366 = load i32, i32* @pos
	%add_228 = add i32 1, %load_366
	store i32 %add_228, i32* @pos
	br label %skip_space_BB1_cp4


skip_space_BB13_cp4:
	%load_367 = load i32, i32* @pos
	%gep_96 = getelementptr i32, i32* %arg_1, i32 %load_367
	%load_368 = load i32, i32* %gep_96
	%icmp_128 = icmp eq i32 %load_368, 13
	br i1 %icmp_128, label %skip_space_BB15_cp4, label %detect_item_BB102


skip_space_BB15_cp4:
	%load_369 = load i32, i32* @pos
	%add_232 = add i32 1, %load_369
	store i32 %add_232, i32* @pos
	br label %skip_space_BB1_cp4


skip_space_BB1_cp5:
	%load_370 = load i32, i32* @pos
	%icmp_129 = icmp sge i32 %load_370, %arg_2
	br i1 %icmp_129, label %skip_space_BB3_cp5, label %skip_space_BB5_cp5


skip_space_BB3_cp5:
	%load_383 = load i32, i32* @pos
	%icmp_134 = icmp sge i32 %load_383, %arg_2
	br i1 %icmp_134, label %detect_item_BB107, label %detect_item_BB108


skip_space_BB5_cp5:
	%load_371 = load i32, i32* @pos
	%gep_97 = getelementptr i32, i32* %arg_1, i32 %load_371
	%load_372 = load i32, i32* %gep_97
	%icmp_130 = icmp eq i32 %load_372, 32
	br i1 %icmp_130, label %skip_space_BB6_cp5, label %skip_space_BB7_cp5


skip_space_BB6_cp5:
	%load_373 = load i32, i32* @pos
	%add_202 = add i32 1, %load_373
	store i32 %add_202, i32* @pos
	br label %skip_space_BB1_cp5


skip_space_BB7_cp5:
	%load_374 = load i32, i32* @pos
	%gep_98 = getelementptr i32, i32* %arg_1, i32 %load_374
	%load_375 = load i32, i32* %gep_98
	%icmp_131 = icmp eq i32 %load_375, 9
	br i1 %icmp_131, label %skip_space_BB9_cp5, label %skip_space_BB10_cp5


skip_space_BB9_cp5:
	%load_376 = load i32, i32* @pos
	%add_212 = add i32 1, %load_376
	store i32 %add_212, i32* @pos
	br label %skip_space_BB1_cp5


skip_space_BB10_cp5:
	%load_377 = load i32, i32* @pos
	%gep_99 = getelementptr i32, i32* %arg_1, i32 %load_377
	%load_378 = load i32, i32* %gep_99
	%icmp_132 = icmp eq i32 %load_378, 10
	br i1 %icmp_132, label %skip_space_BB12_cp5, label %skip_space_BB13_cp5


skip_space_BB12_cp5:
	%load_379 = load i32, i32* @pos
	%add_218 = add i32 1, %load_379
	store i32 %add_218, i32* @pos
	br label %skip_space_BB1_cp5


skip_space_BB13_cp5:
	%load_380 = load i32, i32* @pos
	%gep_100 = getelementptr i32, i32* %arg_1, i32 %load_380
	%load_381 = load i32, i32* %gep_100
	%icmp_133 = icmp eq i32 %load_381, 13
	br i1 %icmp_133, label %skip_space_BB15_cp5, label %skip_space_BB3_cp5


skip_space_BB15_cp5:
	%load_382 = load i32, i32* @pos
	%add_223 = add i32 1, %load_382
	store i32 %add_223, i32* @pos
	br label %skip_space_BB1_cp5


skip_space_BB1_cp6:
	%load_384 = load i32, i32* @pos
	%icmp_135 = icmp sge i32 %load_384, %arg_2
	br i1 %icmp_135, label %skip_space_BB3_cp6, label %skip_space_BB5_cp6


skip_space_BB3_cp6:
	%load_397 = load i32, i32* @pos
	%icmp_140 = icmp slt i32 %load_397, %arg_2
	br i1 %icmp_140, label %detect_item_BB114, label %detect_item_BB115


skip_space_BB5_cp6:
	%load_385 = load i32, i32* @pos
	%gep_101 = getelementptr i32, i32* %arg_1, i32 %load_385
	%load_386 = load i32, i32* %gep_101
	%icmp_136 = icmp eq i32 %load_386, 32
	br i1 %icmp_136, label %skip_space_BB6_cp6, label %skip_space_BB7_cp6


skip_space_BB6_cp6:
	%load_387 = load i32, i32* @pos
	%add_177 = add i32 1, %load_387
	store i32 %add_177, i32* @pos
	br label %skip_space_BB1_cp6


skip_space_BB7_cp6:
	%load_388 = load i32, i32* @pos
	%gep_102 = getelementptr i32, i32* %arg_1, i32 %load_388
	%load_389 = load i32, i32* %gep_102
	%icmp_137 = icmp eq i32 %load_389, 9
	br i1 %icmp_137, label %skip_space_BB9_cp6, label %skip_space_BB10_cp6


skip_space_BB9_cp6:
	%load_390 = load i32, i32* @pos
	%add_184 = add i32 1, %load_390
	store i32 %add_184, i32* @pos
	br label %skip_space_BB1_cp6


skip_space_BB10_cp6:
	%load_391 = load i32, i32* @pos
	%gep_103 = getelementptr i32, i32* %arg_1, i32 %load_391
	%load_392 = load i32, i32* %gep_103
	%icmp_138 = icmp eq i32 %load_392, 10
	br i1 %icmp_138, label %skip_space_BB12_cp6, label %skip_space_BB13_cp6


skip_space_BB12_cp6:
	%load_393 = load i32, i32* @pos
	%add_191 = add i32 1, %load_393
	store i32 %add_191, i32* @pos
	br label %skip_space_BB1_cp6


skip_space_BB13_cp6:
	%load_394 = load i32, i32* @pos
	%gep_104 = getelementptr i32, i32* %arg_1, i32 %load_394
	%load_395 = load i32, i32* %gep_104
	%icmp_139 = icmp eq i32 %load_395, 13
	br i1 %icmp_139, label %skip_space_BB15_cp6, label %skip_space_BB3_cp6


skip_space_BB15_cp6:
	%load_396 = load i32, i32* @pos
	%add_197 = add i32 1, %load_396
	store i32 %add_197, i32* @pos
	br label %skip_space_BB1_cp6


skip_space_BB1_cp7:
	%load_398 = load i32, i32* @pos
	%icmp_141 = icmp sge i32 %load_398, %arg_2
	br i1 %icmp_141, label %skip_space_BB3_cp7, label %skip_space_BB5_cp7


skip_space_BB3_cp7:
	%load_411 = load i32, i32* @pos
	%icmp_146 = icmp sge i32 %load_411, %arg_2
	br i1 %icmp_146, label %detect_item_BB120, label %detect_item_BB121


skip_space_BB5_cp7:
	%load_399 = load i32, i32* @pos
	%gep_105 = getelementptr i32, i32* %arg_1, i32 %load_399
	%load_400 = load i32, i32* %gep_105
	%icmp_142 = icmp eq i32 %load_400, 32
	br i1 %icmp_142, label %skip_space_BB6_cp7, label %skip_space_BB7_cp7


skip_space_BB6_cp7:
	%load_401 = load i32, i32* @pos
	%add_196 = add i32 1, %load_401
	store i32 %add_196, i32* @pos
	br label %skip_space_BB1_cp7


skip_space_BB7_cp7:
	%load_402 = load i32, i32* @pos
	%gep_106 = getelementptr i32, i32* %arg_1, i32 %load_402
	%load_403 = load i32, i32* %gep_106
	%icmp_143 = icmp eq i32 %load_403, 9
	br i1 %icmp_143, label %skip_space_BB9_cp7, label %skip_space_BB10_cp7


skip_space_BB9_cp7:
	%load_404 = load i32, i32* @pos
	%add_205 = add i32 1, %load_404
	store i32 %add_205, i32* @pos
	br label %skip_space_BB1_cp7


skip_space_BB10_cp7:
	%load_405 = load i32, i32* @pos
	%gep_107 = getelementptr i32, i32* %arg_1, i32 %load_405
	%load_406 = load i32, i32* %gep_107
	%icmp_144 = icmp eq i32 %load_406, 10
	br i1 %icmp_144, label %skip_space_BB12_cp7, label %skip_space_BB13_cp7


skip_space_BB12_cp7:
	%load_407 = load i32, i32* @pos
	%add_214 = add i32 1, %load_407
	store i32 %add_214, i32* @pos
	br label %skip_space_BB1_cp7


skip_space_BB13_cp7:
	%load_408 = load i32, i32* @pos
	%gep_108 = getelementptr i32, i32* %arg_1, i32 %load_408
	%load_409 = load i32, i32* %gep_108
	%icmp_145 = icmp eq i32 %load_409, 13
	br i1 %icmp_145, label %skip_space_BB15_cp7, label %skip_space_BB3_cp7


skip_space_BB15_cp7:
	%load_410 = load i32, i32* @pos
	%add_219 = add i32 1, %load_410
	store i32 %add_219, i32* @pos
	br label %skip_space_BB1_cp7


skip_space_BB1_cp8:
	%load_412 = load i32, i32* @pos
	%icmp_147 = icmp sge i32 %load_412, %arg_2
	br i1 %icmp_147, label %skip_space_BB3_cp8, label %skip_space_BB5_cp8


skip_space_BB3_cp8:
	%call_55 = call i32 @detect_item(i32 0, i32* %arg_1, i32 %arg_2)
	%icmp_152 = icmp eq i32 %call_55, 0
	br i1 %icmp_152, label %detect_item_BB124, label %skip_space_BB1_cp9


skip_space_BB5_cp8:
	%load_413 = load i32, i32* @pos
	%gep_109 = getelementptr i32, i32* %arg_1, i32 %load_413
	%load_414 = load i32, i32* %gep_109
	%icmp_148 = icmp eq i32 %load_414, 32
	br i1 %icmp_148, label %skip_space_BB6_cp8, label %skip_space_BB7_cp8


skip_space_BB6_cp8:
	%load_415 = load i32, i32* @pos
	%add_224 = add i32 1, %load_415
	store i32 %add_224, i32* @pos
	br label %skip_space_BB1_cp8


skip_space_BB7_cp8:
	%load_416 = load i32, i32* @pos
	%gep_110 = getelementptr i32, i32* %arg_1, i32 %load_416
	%load_417 = load i32, i32* %gep_110
	%icmp_149 = icmp eq i32 %load_417, 9
	br i1 %icmp_149, label %skip_space_BB9_cp8, label %skip_space_BB10_cp8


skip_space_BB9_cp8:
	%load_418 = load i32, i32* @pos
	%add_227 = add i32 1, %load_418
	store i32 %add_227, i32* @pos
	br label %skip_space_BB1_cp8


skip_space_BB10_cp8:
	%load_419 = load i32, i32* @pos
	%gep_111 = getelementptr i32, i32* %arg_1, i32 %load_419
	%load_420 = load i32, i32* %gep_111
	%icmp_150 = icmp eq i32 %load_420, 10
	br i1 %icmp_150, label %skip_space_BB12_cp8, label %skip_space_BB13_cp8


skip_space_BB12_cp8:
	%load_421 = load i32, i32* @pos
	%add_231 = add i32 1, %load_421
	store i32 %add_231, i32* @pos
	br label %skip_space_BB1_cp8


skip_space_BB13_cp8:
	%load_422 = load i32, i32* @pos
	%gep_112 = getelementptr i32, i32* %arg_1, i32 %load_422
	%load_423 = load i32, i32* %gep_112
	%icmp_151 = icmp eq i32 %load_423, 13
	br i1 %icmp_151, label %skip_space_BB15_cp8, label %skip_space_BB3_cp8


skip_space_BB15_cp8:
	%load_424 = load i32, i32* @pos
	%add_234 = add i32 1, %load_424
	store i32 %add_234, i32* @pos
	br label %skip_space_BB1_cp8


skip_space_BB1_cp9:
	%load_425 = load i32, i32* @pos
	%icmp_153 = icmp sge i32 %load_425, %arg_2
	br i1 %icmp_153, label %detect_item_BB126, label %skip_space_BB5_cp9


skip_space_BB5_cp9:
	%load_426 = load i32, i32* @pos
	%gep_113 = getelementptr i32, i32* %arg_1, i32 %load_426
	%load_427 = load i32, i32* %gep_113
	%icmp_154 = icmp eq i32 %load_427, 32
	br i1 %icmp_154, label %skip_space_BB6_cp9, label %skip_space_BB7_cp9


skip_space_BB6_cp9:
	%load_428 = load i32, i32* @pos
	%add_230 = add i32 1, %load_428
	store i32 %add_230, i32* @pos
	br label %skip_space_BB1_cp9


skip_space_BB7_cp9:
	%load_429 = load i32, i32* @pos
	%gep_114 = getelementptr i32, i32* %arg_1, i32 %load_429
	%load_430 = load i32, i32* %gep_114
	%icmp_155 = icmp eq i32 %load_430, 9
	br i1 %icmp_155, label %skip_space_BB9_cp9, label %skip_space_BB10_cp9


skip_space_BB9_cp9:
	%load_431 = load i32, i32* @pos
	%add_233 = add i32 1, %load_431
	store i32 %add_233, i32* @pos
	br label %skip_space_BB1_cp9


skip_space_BB10_cp9:
	%load_432 = load i32, i32* @pos
	%gep_115 = getelementptr i32, i32* %arg_1, i32 %load_432
	%load_433 = load i32, i32* %gep_115
	%icmp_156 = icmp eq i32 %load_433, 10
	br i1 %icmp_156, label %skip_space_BB12_cp9, label %skip_space_BB13_cp9


skip_space_BB12_cp9:
	%load_434 = load i32, i32* @pos
	%add_236 = add i32 1, %load_434
	store i32 %add_236, i32* @pos
	br label %skip_space_BB1_cp9


skip_space_BB13_cp9:
	%load_435 = load i32, i32* @pos
	%gep_116 = getelementptr i32, i32* %arg_1, i32 %load_435
	%load_436 = load i32, i32* %gep_116
	%icmp_157 = icmp eq i32 %load_436, 13
	br i1 %icmp_157, label %skip_space_BB15_cp9, label %detect_item_BB126


skip_space_BB15_cp9:
	%load_437 = load i32, i32* @pos
	%add_240 = add i32 1, %load_437
	store i32 %add_240, i32* @pos
	br label %skip_space_BB1_cp9


skip_space_BB1_cp10:
	%load_438 = load i32, i32* @pos
	%icmp_158 = icmp sge i32 %load_438, %arg_2
	br i1 %icmp_158, label %skip_space_BB3_cp10, label %skip_space_BB5_cp10


skip_space_BB3_cp10:
	%call_56 = call i32 @detect_item(i32 2, i32* %arg_1, i32 %arg_2)
	%icmp_163 = icmp eq i32 %call_56, 0
	br i1 %icmp_163, label %detect_item_BB129, label %skip_space_BB1_cp11


skip_space_BB5_cp10:
	%load_439 = load i32, i32* @pos
	%gep_117 = getelementptr i32, i32* %arg_1, i32 %load_439
	%load_440 = load i32, i32* %gep_117
	%icmp_159 = icmp eq i32 %load_440, 32
	br i1 %icmp_159, label %skip_space_BB6_cp10, label %skip_space_BB7_cp10


skip_space_BB6_cp10:
	%load_441 = load i32, i32* @pos
	%add_237 = add i32 1, %load_441
	store i32 %add_237, i32* @pos
	br label %skip_space_BB1_cp10


skip_space_BB7_cp10:
	%load_442 = load i32, i32* @pos
	%gep_118 = getelementptr i32, i32* %arg_1, i32 %load_442
	%load_443 = load i32, i32* %gep_118
	%icmp_160 = icmp eq i32 %load_443, 9
	br i1 %icmp_160, label %skip_space_BB9_cp10, label %skip_space_BB10_cp10


skip_space_BB9_cp10:
	%load_444 = load i32, i32* @pos
	%add_241 = add i32 1, %load_444
	store i32 %add_241, i32* @pos
	br label %skip_space_BB1_cp10


skip_space_BB10_cp10:
	%load_445 = load i32, i32* @pos
	%gep_119 = getelementptr i32, i32* %arg_1, i32 %load_445
	%load_446 = load i32, i32* %gep_119
	%icmp_161 = icmp eq i32 %load_446, 10
	br i1 %icmp_161, label %skip_space_BB12_cp10, label %skip_space_BB13_cp10


skip_space_BB12_cp10:
	%load_447 = load i32, i32* @pos
	%add_244 = add i32 1, %load_447
	store i32 %add_244, i32* @pos
	br label %skip_space_BB1_cp10


skip_space_BB13_cp10:
	%load_448 = load i32, i32* @pos
	%gep_120 = getelementptr i32, i32* %arg_1, i32 %load_448
	%load_449 = load i32, i32* %gep_120
	%icmp_162 = icmp eq i32 %load_449, 13
	br i1 %icmp_162, label %skip_space_BB15_cp10, label %skip_space_BB3_cp10


skip_space_BB15_cp10:
	%load_450 = load i32, i32* @pos
	%add_248 = add i32 1, %load_450
	store i32 %add_248, i32* @pos
	br label %skip_space_BB1_cp10


skip_space_BB1_cp11:
	%load_451 = load i32, i32* @pos
	%icmp_164 = icmp sge i32 %load_451, %arg_2
	br i1 %icmp_164, label %skip_space_BB3_cp11, label %skip_space_BB5_cp11


skip_space_BB3_cp11:
	%load_464 = load i32, i32* @pos
	%icmp_169 = icmp sge i32 %load_464, %arg_2
	br i1 %icmp_169, label %detect_item_BB131, label %detect_item_BB132


skip_space_BB5_cp11:
	%load_452 = load i32, i32* @pos
	%gep_121 = getelementptr i32, i32* %arg_1, i32 %load_452
	%load_453 = load i32, i32* %gep_121
	%icmp_165 = icmp eq i32 %load_453, 32
	br i1 %icmp_165, label %skip_space_BB6_cp11, label %skip_space_BB7_cp11


skip_space_BB6_cp11:
	%load_454 = load i32, i32* @pos
	%add_243 = add i32 1, %load_454
	store i32 %add_243, i32* @pos
	br label %skip_space_BB1_cp11


skip_space_BB7_cp11:
	%load_455 = load i32, i32* @pos
	%gep_122 = getelementptr i32, i32* %arg_1, i32 %load_455
	%load_456 = load i32, i32* %gep_122
	%icmp_166 = icmp eq i32 %load_456, 9
	br i1 %icmp_166, label %skip_space_BB9_cp11, label %skip_space_BB10_cp11


skip_space_BB9_cp11:
	%load_457 = load i32, i32* @pos
	%add_247 = add i32 1, %load_457
	store i32 %add_247, i32* @pos
	br label %skip_space_BB1_cp11


skip_space_BB10_cp11:
	%load_458 = load i32, i32* @pos
	%gep_123 = getelementptr i32, i32* %arg_1, i32 %load_458
	%load_459 = load i32, i32* %gep_123
	%icmp_167 = icmp eq i32 %load_459, 10
	br i1 %icmp_167, label %skip_space_BB12_cp11, label %skip_space_BB13_cp11


skip_space_BB12_cp11:
	%load_460 = load i32, i32* @pos
	%add_249 = add i32 1, %load_460
	store i32 %add_249, i32* @pos
	br label %skip_space_BB1_cp11


skip_space_BB13_cp11:
	%load_461 = load i32, i32* @pos
	%gep_124 = getelementptr i32, i32* %arg_1, i32 %load_461
	%load_462 = load i32, i32* %gep_124
	%icmp_168 = icmp eq i32 %load_462, 13
	br i1 %icmp_168, label %skip_space_BB15_cp11, label %skip_space_BB3_cp11


skip_space_BB15_cp11:
	%load_463 = load i32, i32* @pos
	%add_250 = add i32 1, %load_463
	store i32 %add_250, i32* @pos
	br label %skip_space_BB1_cp11


skip_space_BB1_cp12:
	%load_465 = load i32, i32* @pos
	%icmp_170 = icmp sge i32 %load_465, %arg_2
	br i1 %icmp_170, label %skip_space_BB3_cp12, label %skip_space_BB5_cp12


skip_space_BB3_cp12:
	%call_57 = call i32 @detect_item(i32 0, i32* %arg_1, i32 %arg_2)
	%icmp_175 = icmp eq i32 %call_57, 0
	br i1 %icmp_175, label %detect_item_BB135, label %skip_space_BB1_cp13


skip_space_BB5_cp12:
	%load_466 = load i32, i32* @pos
	%gep_125 = getelementptr i32, i32* %arg_1, i32 %load_466
	%load_467 = load i32, i32* %gep_125
	%icmp_171 = icmp eq i32 %load_467, 32
	br i1 %icmp_171, label %skip_space_BB6_cp12, label %skip_space_BB7_cp12


skip_space_BB6_cp12:
	%load_468 = load i32, i32* @pos
	%add_251 = add i32 1, %load_468
	store i32 %add_251, i32* @pos
	br label %skip_space_BB1_cp12


skip_space_BB7_cp12:
	%load_469 = load i32, i32* @pos
	%gep_126 = getelementptr i32, i32* %arg_1, i32 %load_469
	%load_470 = load i32, i32* %gep_126
	%icmp_172 = icmp eq i32 %load_470, 9
	br i1 %icmp_172, label %skip_space_BB9_cp12, label %skip_space_BB10_cp12


skip_space_BB9_cp12:
	%load_471 = load i32, i32* @pos
	%add_252 = add i32 1, %load_471
	store i32 %add_252, i32* @pos
	br label %skip_space_BB1_cp12


skip_space_BB10_cp12:
	%load_472 = load i32, i32* @pos
	%gep_127 = getelementptr i32, i32* %arg_1, i32 %load_472
	%load_473 = load i32, i32* %gep_127
	%icmp_173 = icmp eq i32 %load_473, 10
	br i1 %icmp_173, label %skip_space_BB12_cp12, label %skip_space_BB13_cp12


skip_space_BB12_cp12:
	%load_474 = load i32, i32* @pos
	%add_254 = add i32 1, %load_474
	store i32 %add_254, i32* @pos
	br label %skip_space_BB1_cp12


skip_space_BB13_cp12:
	%load_475 = load i32, i32* @pos
	%gep_128 = getelementptr i32, i32* %arg_1, i32 %load_475
	%load_476 = load i32, i32* %gep_128
	%icmp_174 = icmp eq i32 %load_476, 13
	br i1 %icmp_174, label %skip_space_BB15_cp12, label %skip_space_BB3_cp12


skip_space_BB15_cp12:
	%load_477 = load i32, i32* @pos
	%add_256 = add i32 1, %load_477
	store i32 %add_256, i32* @pos
	br label %skip_space_BB1_cp12


skip_space_BB1_cp13:
	%load_478 = load i32, i32* @pos
	%icmp_176 = icmp sge i32 %load_478, %arg_2
	br i1 %icmp_176, label %detect_item_BB126, label %skip_space_BB5_cp13


skip_space_BB5_cp13:
	%load_479 = load i32, i32* @pos
	%gep_129 = getelementptr i32, i32* %arg_1, i32 %load_479
	%load_480 = load i32, i32* %gep_129
	%icmp_177 = icmp eq i32 %load_480, 32
	br i1 %icmp_177, label %skip_space_BB6_cp13, label %skip_space_BB7_cp13


skip_space_BB6_cp13:
	%load_481 = load i32, i32* @pos
	%add_253 = add i32 1, %load_481
	store i32 %add_253, i32* @pos
	br label %skip_space_BB1_cp13


skip_space_BB7_cp13:
	%load_482 = load i32, i32* @pos
	%gep_130 = getelementptr i32, i32* %arg_1, i32 %load_482
	%load_483 = load i32, i32* %gep_130
	%icmp_178 = icmp eq i32 %load_483, 9
	br i1 %icmp_178, label %skip_space_BB9_cp13, label %skip_space_BB10_cp13


skip_space_BB9_cp13:
	%load_484 = load i32, i32* @pos
	%add_255 = add i32 1, %load_484
	store i32 %add_255, i32* @pos
	br label %skip_space_BB1_cp13


skip_space_BB10_cp13:
	%load_485 = load i32, i32* @pos
	%gep_131 = getelementptr i32, i32* %arg_1, i32 %load_485
	%load_486 = load i32, i32* %gep_131
	%icmp_179 = icmp eq i32 %load_486, 10
	br i1 %icmp_179, label %skip_space_BB12_cp13, label %skip_space_BB13_cp13


skip_space_BB12_cp13:
	%load_487 = load i32, i32* @pos
	%add_257 = add i32 1, %load_487
	store i32 %add_257, i32* @pos
	br label %skip_space_BB1_cp13


skip_space_BB13_cp13:
	%load_488 = load i32, i32* @pos
	%gep_132 = getelementptr i32, i32* %arg_1, i32 %load_488
	%load_489 = load i32, i32* %gep_132
	%icmp_180 = icmp eq i32 %load_489, 13
	br i1 %icmp_180, label %skip_space_BB15_cp13, label %detect_item_BB126


skip_space_BB15_cp13:
	%load_490 = load i32, i32* @pos
	%add_258 = add i32 1, %load_490
	store i32 %add_258, i32* @pos
	br label %skip_space_BB1_cp13


skip_space_BB1_cp14:
	%load_491 = load i32, i32* @pos
	%icmp_181 = icmp sge i32 %load_491, %arg_2
	br i1 %icmp_181, label %skip_space_BB3_cp14, label %skip_space_BB5_cp14


skip_space_BB3_cp14:
	%load_504 = load i32, i32* @pos
	%icmp_186 = icmp sge i32 %load_504, %arg_2
	br i1 %icmp_186, label %detect_item_BB137, label %detect_item_BB138


skip_space_BB5_cp14:
	%load_492 = load i32, i32* @pos
	%gep_133 = getelementptr i32, i32* %arg_1, i32 %load_492
	%load_493 = load i32, i32* %gep_133
	%icmp_182 = icmp eq i32 %load_493, 32
	br i1 %icmp_182, label %skip_space_BB6_cp14, label %skip_space_BB7_cp14


skip_space_BB6_cp14:
	%load_494 = load i32, i32* @pos
	%add_235 = add i32 1, %load_494
	store i32 %add_235, i32* @pos
	br label %skip_space_BB1_cp14


skip_space_BB7_cp14:
	%load_495 = load i32, i32* @pos
	%gep_134 = getelementptr i32, i32* %arg_1, i32 %load_495
	%load_496 = load i32, i32* %gep_134
	%icmp_183 = icmp eq i32 %load_496, 9
	br i1 %icmp_183, label %skip_space_BB9_cp14, label %skip_space_BB10_cp14


skip_space_BB9_cp14:
	%load_497 = load i32, i32* @pos
	%add_239 = add i32 1, %load_497
	store i32 %add_239, i32* @pos
	br label %skip_space_BB1_cp14


skip_space_BB10_cp14:
	%load_498 = load i32, i32* @pos
	%gep_135 = getelementptr i32, i32* %arg_1, i32 %load_498
	%load_499 = load i32, i32* %gep_135
	%icmp_184 = icmp eq i32 %load_499, 10
	br i1 %icmp_184, label %skip_space_BB12_cp14, label %skip_space_BB13_cp14


skip_space_BB12_cp14:
	%load_500 = load i32, i32* @pos
	%add_242 = add i32 1, %load_500
	store i32 %add_242, i32* @pos
	br label %skip_space_BB1_cp14


skip_space_BB13_cp14:
	%load_501 = load i32, i32* @pos
	%gep_136 = getelementptr i32, i32* %arg_1, i32 %load_501
	%load_502 = load i32, i32* %gep_136
	%icmp_185 = icmp eq i32 %load_502, 13
	br i1 %icmp_185, label %skip_space_BB15_cp14, label %skip_space_BB3_cp14


skip_space_BB15_cp14:
	%load_503 = load i32, i32* @pos
	%add_245 = add i32 1, %load_503
	store i32 %add_245, i32* @pos
	br label %skip_space_BB1_cp14


}

define i32 @main() {
main_BB0:
	%call_38 = call i32 @getch()
	move %phi_1 <-- %call_38
	move %phi_0 <-- 0
	br label %main_BB1


main_BB1:
	%icmp_84 = icmp ne i32 %phi_1, 35
	br i1 %icmp_84, label %main_BB2, label %main_BB3


main_BB2:
	%gep_73 = getelementptr [50000000 x i32], [50000000 x i32]* @buffer, i32 0, i32 %phi_0
	store i32 %phi_1, i32* %gep_73
	%add_259 = add i32 1, %phi_0
	%call_39 = call i32 @getch()
	move %phi_1 <-- %call_39
	move %phi_0 <-- %add_259
	br label %main_BB1


main_BB3:
	call void @putint(i32 1)
	%gep_74 = getelementptr [50000000 x i32], [50000000 x i32]* @buffer, i32 0, i32 0
	br label %skip_space_BB1_cp15


main_BB4:
	call void @putch(i32 111)
	call void @putch(i32 107)
	call void @putch(i32 10)
	ret i32 0


main_BB5:
	call void @putch(i32 110)
	call void @putch(i32 111)
	call void @putch(i32 116)
	call void @putch(i32 32)
	call void @putch(i32 111)
	call void @putch(i32 107)
	call void @putch(i32 10)
	ret i32 1


skip_space_BB1_cp15:
	%load_505 = load i32, i32* @pos
	%icmp_187 = icmp sge i32 %load_505, %phi_0
	br i1 %icmp_187, label %skip_space_BB3_cp15, label %skip_space_BB5_cp15


skip_space_BB3_cp15:
	%call_58 = call i32 @detect_item(i32 0, i32* %gep_74, i32 %phi_0)
	br label %skip_space_BB1_cp16


skip_space_BB5_cp15:
	%load_506 = load i32, i32* @pos
	%gep_152 = getelementptr [50000000 x i32], [50000000 x i32]* @buffer, i32 0, i32 %load_506
	%load_507 = load i32, i32* %gep_152
	%icmp_188 = icmp eq i32 %load_507, 32
	br i1 %icmp_188, label %skip_space_BB6_cp15, label %skip_space_BB7_cp15


skip_space_BB6_cp15:
	%load_508 = load i32, i32* @pos
	%add_260 = add i32 1, %load_508
	store i32 %add_260, i32* @pos
	br label %skip_space_BB1_cp15


skip_space_BB7_cp15:
	%load_509 = load i32, i32* @pos
	%gep_151 = getelementptr [50000000 x i32], [50000000 x i32]* @buffer, i32 0, i32 %load_509
	%load_510 = load i32, i32* %gep_151
	%icmp_189 = icmp eq i32 %load_510, 9
	br i1 %icmp_189, label %skip_space_BB9_cp15, label %skip_space_BB10_cp15


skip_space_BB9_cp15:
	%load_511 = load i32, i32* @pos
	%add_261 = add i32 1, %load_511
	store i32 %add_261, i32* @pos
	br label %skip_space_BB1_cp15


skip_space_BB10_cp15:
	%load_512 = load i32, i32* @pos
	%gep_149 = getelementptr [50000000 x i32], [50000000 x i32]* @buffer, i32 0, i32 %load_512
	%load_513 = load i32, i32* %gep_149
	%icmp_190 = icmp eq i32 %load_513, 10
	br i1 %icmp_190, label %skip_space_BB12_cp15, label %skip_space_BB13_cp15


skip_space_BB12_cp15:
	%load_514 = load i32, i32* @pos
	%add_263 = add i32 1, %load_514
	store i32 %add_263, i32* @pos
	br label %skip_space_BB1_cp15


skip_space_BB13_cp15:
	%load_515 = load i32, i32* @pos
	%gep_147 = getelementptr [50000000 x i32], [50000000 x i32]* @buffer, i32 0, i32 %load_515
	%load_516 = load i32, i32* %gep_147
	%icmp_191 = icmp eq i32 %load_516, 13
	br i1 %icmp_191, label %skip_space_BB15_cp15, label %skip_space_BB3_cp15


skip_space_BB15_cp15:
	%load_517 = load i32, i32* @pos
	%add_265 = add i32 1, %load_517
	store i32 %add_265, i32* @pos
	br label %skip_space_BB1_cp15


skip_space_BB1_cp16:
	%load_518 = load i32, i32* @pos
	%icmp_193 = icmp sge i32 %load_518, %phi_0
	br i1 %icmp_193, label %skip_space_BB3_cp16, label %skip_space_BB5_cp16


skip_space_BB3_cp16:
	%icmp_198 = icmp ne i32 %call_58, 0
	br i1 %icmp_198, label %main_BB4, label %main_BB5


skip_space_BB5_cp16:
	%load_519 = load i32, i32* @pos
	%gep_150 = getelementptr [50000000 x i32], [50000000 x i32]* @buffer, i32 0, i32 %load_519
	%load_520 = load i32, i32* %gep_150
	%icmp_194 = icmp eq i32 %load_520, 32
	br i1 %icmp_194, label %skip_space_BB6_cp16, label %skip_space_BB7_cp16


skip_space_BB6_cp16:
	%load_521 = load i32, i32* @pos
	%add_262 = add i32 1, %load_521
	store i32 %add_262, i32* @pos
	br label %skip_space_BB1_cp16


skip_space_BB7_cp16:
	%load_522 = load i32, i32* @pos
	%gep_148 = getelementptr [50000000 x i32], [50000000 x i32]* @buffer, i32 0, i32 %load_522
	%load_523 = load i32, i32* %gep_148
	%icmp_195 = icmp eq i32 %load_523, 9
	br i1 %icmp_195, label %skip_space_BB9_cp16, label %skip_space_BB10_cp16


skip_space_BB9_cp16:
	%load_524 = load i32, i32* @pos
	%add_264 = add i32 1, %load_524
	store i32 %add_264, i32* @pos
	br label %skip_space_BB1_cp16


skip_space_BB10_cp16:
	%load_525 = load i32, i32* @pos
	%gep_146 = getelementptr [50000000 x i32], [50000000 x i32]* @buffer, i32 0, i32 %load_525
	%load_526 = load i32, i32* %gep_146
	%icmp_196 = icmp eq i32 %load_526, 10
	br i1 %icmp_196, label %skip_space_BB12_cp16, label %skip_space_BB13_cp16


skip_space_BB12_cp16:
	%load_527 = load i32, i32* @pos
	%add_266 = add i32 1, %load_527
	store i32 %add_266, i32* @pos
	br label %skip_space_BB1_cp16


skip_space_BB13_cp16:
	%load_528 = load i32, i32* @pos
	%gep_145 = getelementptr [50000000 x i32], [50000000 x i32]* @buffer, i32 0, i32 %load_528
	%load_529 = load i32, i32* %gep_145
	%icmp_197 = icmp eq i32 %load_529, 13
	br i1 %icmp_197, label %skip_space_BB15_cp16, label %skip_space_BB3_cp16


skip_space_BB15_cp16:
	%load_530 = load i32, i32* @pos
	%add_267 = add i32 1, %load_530
	store i32 %add_267, i32* @pos
	br label %skip_space_BB1_cp16


}


LOCAL_MODULE_CLASS := EXECUTABLES
LOCAL_MODULE_SUFFIX := 
LOCAL_FORCE_STATIC_EXECUTABLE := true

include $(BUILD_SYSTEM)/binary.mk

$(LOCAL_BUILT_MODULE) : PRIVATE_ELF_FILE := $(intermediates)/$(PRIVATE_MODULE).elf
$(LOCAL_BUILT_MODULE) : PRIVATE_LIBS := `$(TARGET_CC) -mthumb-interwork -print-libgcc-file-name`

$(all_objects) : PRIVATE_TARGET_PROJECT_INCLUDES :=
$(all_objects) : PRIVATE_TARGET_C_INCLUDES :=
$(all_objects) : PRIVATE_TARGET_GLOBAL_CFLAGS :=
$(all_objects) : PRIVATE_TARGET_GLOBAL_CPPFLAGS :=

ifeq ($(strip $(LOCAL_TRUSTZONE_BIN)),true)
TRUSTZONE_BIN := $(PRODUCT_OUT)/$(LOCAL_MODULE)
TMP_TRUSTZONE_BIN := $(PRODUCT_OUT)/$(addprefix tmp_,$(LOCAL_MODULE))
$(LOCAL_BUILT_MODULE): $(all_objects) $(all_libraries)
$(LOCAL_BUILT_MODULE): $(MTEE_DEPEND_FILE_LIST)
	@$(mkdir -p $(dir $@)
	@echo "target Linking: $(PRIVATE_MODULE)"
	$(hide) $(TARGET_LD) \
		$(addprefix --script ,$(PRIVATE_LINK_SCRIPT)) \
		$(PRIVATE_RAW_EXECUTABLE_LDFLAGS) \
		-o $(PRIVATE_ELF_FILE) \
		$(PRIVATE_ALL_OBJECTS) \
                --whole-archive $(filter-out %$(TZ_C_LIBRARIES).a,$(PRIVATE_ALL_STATIC_LIBRARIES)) --no-whole-archive \
		--start-group $(filter %$(TZ_C_LIBRARIES).a,$(PRIVATE_ALL_STATIC_LIBRARIES)) \
		$(PRIVATE_LIBS) --end-group
	$(hide) $(TARGET_OBJCOPY) -O binary $(PRIVATE_ELF_FILE) $@
	@echo "protecting mtee image ($@ -> $(TMP_TRUSTZONE_BIN))"
	$(hide) $(MTEE_IMG_PROT_TOOL) $(MTEE_IMG_PROT_CFG) $@ $(TMP_TRUSTZONE_BIN) $(MEMSIZE)
	@echo "generating mtee image ($(TMP_TRUSTZONE_BIN) -> $(TRUSTZONE_BIN))"
	$(hide) $(MK_IMG_TOOL) $(TMP_TRUSTZONE_BIN) TEE > $(TRUSTZONE_BIN)
	$(hide) rm $(TMP_TRUSTZONE_BIN)
#	$(hide) $(MK_IMG_TOOL) $@ TEE > $(TRUSTZONE_BIN)

else
$(LOCAL_BUILT_MODULE): $(all_objects) $(all_libraries)
	@$(mkdir -p $(dir $@)
	@echo "target Linking: $(PRIVATE_MODULE)"
	$(hide) $(TARGET_LD) \
		$(addprefix --script ,$(PRIVATE_LINK_SCRIPT)) \
		$(PRIVATE_RAW_EXECUTABLE_LDFLAGS) \
		-o $(PRIVATE_ELF_FILE) \
		$(PRIVATE_ALL_OBJECTS) \
		--start-group $(PRIVATE_ALL_STATIC_LIBRARIES) --end-group \
		$(PRIVATE_LIBS)
	$(hide) $(TARGET_OBJCOPY) -O binary $(PRIVATE_ELF_FILE) $@
endif

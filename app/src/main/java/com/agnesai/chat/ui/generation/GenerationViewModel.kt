package com.agnesai.chat.ui.generation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.agnesai.chat.data.generation.GenerationParams
import com.agnesai.chat.data.generation.GenerationParamsCodec
import com.agnesai.chat.data.generation.GenerationRepository
import com.agnesai.chat.data.local.MessageStatus
import com.agnesai.chat.data.local.SessionType
import com.agnesai.chat.data.network.IMAGE_MODEL_2_0
import com.agnesai.chat.data.repository.ChatRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class GenerationViewModel(
    private val repository: GenerationRepository,
    private val chatRepository: ChatRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(GenerationUiState())
    val uiState = _uiState.asStateFlow()

    // ========== 图片生成 ==========

    fun updateImagePrompt(prompt: String) {
        _uiState.update { it.copy(image = it.image.copy(prompt = prompt, error = null)) }
    }

    fun setImageModel(model: String) {
        _uiState.update { it.copy(image = it.image.copy(imageModel = model)) }
    }

    fun setImageRatio(ratio: String) {
        _uiState.update { it.copy(image = it.image.copy(ratio = ratio)) }
    }

    fun addReferenceImage(dataUri: String) {
        _uiState.update { state ->
            val images = (state.image.referenceImages + dataUri).take(6)
            state.copy(image = state.image.copy(referenceImages = images))
        }
    }

    fun removeReferenceImage(index: Int) {
        _uiState.update { state ->
            state.copy(
                image = state.image.copy(
                    referenceImages = state.image.referenceImages.filterIndexed { i, _ -> i != index }
                )
            )
        }
    }

    /**
     * 以对话方式发送图片生成请求：提示词与结果作为消息持久化到图片会话。
     *
     * @return true 表示请求已受理，false 表示被拒绝（提示词为空/生成中/无会话）
     */
    fun sendImagePrompt(sessionId: Long): Boolean {
        val state = _uiState.value.image
        val prompt = state.prompt.trim()
        // 仅当当前会话自身在生成时才拒绝，其他会话后台生成中的任务不阻塞本会话
        if (prompt.isEmpty() || sessionId in state.generatingSessionIds || sessionId == 0L) return false

        val params = GenerationParams(
            type = SessionType.IMAGE,
            model = state.imageModel,
            ratio = state.ratio,
            referenceImages = state.referenceImages
        )
        val paramsJson = GenerationParamsCodec.encode(params)

        viewModelScope.launch {
            chatRepository.updateSessionTitleIfFirst(sessionId, prompt.take(20))
            chatRepository.insertUserGenerationMessage(sessionId, prompt, paramsJson)
            _uiState.update {
                it.copy(
                    image = it.image.copy(
                        isGenerating = true,
                        generatingSessionIds = it.image.generatingSessionIds + sessionId,
                        error = null,
                        result = null,
                        prompt = ""
                    ),
                    lastImagePrompt = prompt
                )
            }
            val result = repository.generateImage(
                prompt = prompt,
                model = state.imageModel,
                size = imageSizeFor(state.imageModel),
                ratio = imageRatioFor(state.imageModel, state.ratio),
                referenceImages = state.referenceImages
            )
            result.fold(
                onSuccess = { url ->
                    chatRepository.insertAssistantGenerationMessage(sessionId, url, paramsJson, MessageStatus.DONE)
                    _uiState.update { st ->
                        st.copy(
                            image = st.image.copy(
                                isGenerating = false,
                                generatingSessionIds = st.image.generatingSessionIds - sessionId,
                                result = url
                            )
                        )
                    }
                },
                onFailure = { e ->
                    // 协程取消（退出页面/销毁 ViewModel）时不做失败落库与界面更新
                    if (e is CancellationException) return@fold
                    val message = e.message ?: "图片生成失败，请稍后重试"
                    chatRepository.insertAssistantGenerationMessage(sessionId, message, paramsJson, MessageStatus.ERROR)
                    _uiState.update { st ->
                        st.copy(
                            image = st.image.copy(
                                isGenerating = false,
                                generatingSessionIds = st.image.generatingSessionIds - sessionId,
                                error = message
                            )
                        )
                    }
                }
            )
        }
        return true
    }

    /** 使用当前参数再次生成图片（不新增用户提示词消息），结果写回当前会话。 */
    fun regenerateImage(sessionId: Long) {
        val state = _uiState.value.image
        if (sessionId in state.generatingSessionIds) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    image = it.image.copy(
                        isGenerating = true,
                        generatingSessionIds = it.image.generatingSessionIds + sessionId,
                        error = null,
                        result = null
                    )
                )
            }
            val result = repository.generateImage(
                prompt = state.prompt.ifBlank { _uiState.value.lastImagePrompt },
                model = state.imageModel,
                size = imageSizeFor(state.imageModel),
                ratio = imageRatioFor(state.imageModel, state.ratio),
                referenceImages = state.referenceImages
            )
            result.fold(
                onSuccess = { url ->
                    _uiState.update { st ->
                        st.copy(
                            image = st.image.copy(
                                isGenerating = false,
                                generatingSessionIds = st.image.generatingSessionIds - sessionId,
                                result = url
                            )
                        )
                    }
                },
                onFailure = { e ->
                    if (e is CancellationException) return@fold
                    _uiState.update { st ->
                        st.copy(
                            image = st.image.copy(
                                isGenerating = false,
                                generatingSessionIds = st.image.generatingSessionIds - sessionId,
                                error = e.message ?: "图片生成失败，请稍后重试"
                            )
                        )
                    }
                }
            )
        }
    }

    fun resetImage() {
        _uiState.update {
            it.copy(image = ImageGenState(imageModel = it.image.imageModel))
        }
    }

    // ========== 视频生成 ==========

    fun updateVideoPrompt(prompt: String) {
        _uiState.update { it.copy(video = it.video.copy(prompt = prompt, error = null)) }
    }

    fun setFirstFrameImage(dataUri: String?) {
        _uiState.update { it.copy(video = it.video.copy(firstFrameImage = dataUri)) }
    }

    fun setLastFrameImage(dataUri: String?) {
        _uiState.update { it.copy(video = it.video.copy(lastFrameImage = dataUri)) }
    }

    fun setVideoDuration(duration: String) {
        _uiState.update { it.copy(video = it.video.copy(duration = duration)) }
    }

    fun setVideoQuality(quality: String) {
        _uiState.update { it.copy(video = it.video.copy(quality = quality)) }
    }

    fun setVideoRatio(ratio: String) {
        _uiState.update { it.copy(video = it.video.copy(ratio = ratio)) }
    }

    /**
     * 以对话方式发送视频生成请求：提示词与结果作为消息持久化到视频会话。
     *
     * @return true 表示请求已受理，false 表示被拒绝（提示词为空/生成中/无会话）
     */
    fun sendVideoPrompt(sessionId: Long): Boolean {
        val state = _uiState.value.video
        val prompt = state.prompt.trim()
        // 仅当当前会话自身在生成时才拒绝，其他会话后台生成中的任务不阻塞本会话
        if (prompt.isEmpty() || sessionId in state.generatingSessionIds || sessionId == 0L) return false

        val params = GenerationParams(
            type = SessionType.VIDEO,
            duration = state.duration,
            quality = state.quality,
            ratio = state.ratio,
            firstFrameImage = state.firstFrameImage,
            lastFrameImage = state.lastFrameImage
        )
        val paramsJson = GenerationParamsCodec.encode(params)

        viewModelScope.launch {
            chatRepository.updateSessionTitleIfFirst(sessionId, prompt.take(20))
            chatRepository.insertUserGenerationMessage(sessionId, prompt, paramsJson)
            _uiState.update {
                it.copy(
                    video = it.video.copy(
                        isGenerating = true,
                        generatingSessionIds = it.video.generatingSessionIds + sessionId,
                        error = null,
                        result = null,
                        prompt = ""
                    ),
                    lastVideoPrompt = prompt
                )
            }
            val result = repository.generateVideo(
                prompt = prompt,
                firstFrameImage = state.firstFrameImage,
                lastFrameImage = state.lastFrameImage,
                duration = state.duration,
                quality = state.quality,
                ratio = state.ratio
            )
            result.fold(
                onSuccess = { url ->
                    chatRepository.insertAssistantGenerationMessage(sessionId, url, paramsJson, MessageStatus.DONE)
                    _uiState.update { st ->
                        st.copy(
                            video = st.video.copy(
                                isGenerating = false,
                                generatingSessionIds = st.video.generatingSessionIds - sessionId,
                                result = url
                            )
                        )
                    }
                },
                onFailure = { e ->
                    if (e is CancellationException) return@fold
                    val message = e.message ?: "视频生成失败，请稍后重试"
                    chatRepository.insertAssistantGenerationMessage(sessionId, message, paramsJson, MessageStatus.ERROR)
                    _uiState.update { st ->
                        st.copy(
                            video = st.video.copy(
                                isGenerating = false,
                                generatingSessionIds = st.video.generatingSessionIds - sessionId,
                                error = message
                            )
                        )
                    }
                }
            )
        }
        return true
    }

    /** 使用当前参数再次生成视频（不新增用户提示词消息），结果写回当前会话。 */
    fun regenerateVideo(sessionId: Long) {
        val state = _uiState.value.video
        if (sessionId in state.generatingSessionIds) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    video = it.video.copy(
                        isGenerating = true,
                        generatingSessionIds = it.video.generatingSessionIds + sessionId,
                        error = null,
                        result = null
                    )
                )
            }
            val result = repository.generateVideo(
                prompt = state.prompt.ifBlank { _uiState.value.lastVideoPrompt },
                firstFrameImage = state.firstFrameImage,
                lastFrameImage = state.lastFrameImage,
                duration = state.duration,
                quality = state.quality,
                ratio = state.ratio
            )
            result.fold(
                onSuccess = { url ->
                    _uiState.update { st ->
                        st.copy(
                            video = st.video.copy(
                                isGenerating = false,
                                generatingSessionIds = st.video.generatingSessionIds - sessionId,
                                result = url
                            )
                        )
                    }
                },
                onFailure = { e ->
                    if (e is CancellationException) return@fold
                    _uiState.update { st ->
                        st.copy(
                            video = st.video.copy(
                                isGenerating = false,
                                generatingSessionIds = st.video.generatingSessionIds - sessionId,
                                error = e.message ?: "视频生成失败，请稍后重试"
                            )
                        )
                    }
                }
            )
        }
    }

    fun resetVideo() {
        _uiState.update { it.copy(video = VideoGenState()) }
    }

    // ========== 工具 ==========

    /** 2.1 模型使用档位尺寸，2.0 模型使用精确尺寸。 */
    private fun imageSizeFor(model: String): String? =
        if (model == IMAGE_MODEL_2_0) "1024x1024" else "2K"

    private fun imageRatioFor(model: String, ratio: String): String? =
        if (model == IMAGE_MODEL_2_0) null else ratio
}

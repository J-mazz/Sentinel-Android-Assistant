#include "native_state.hpp"

namespace sentinel_native {

std::shared_mutex g_model_mutex;
ModelState g_state;

} // namespace sentinel_native

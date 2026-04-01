import {computed, reactive} from 'vue'

/** 认证状态管理 */
interface AuthUser {
    id?: number
    username: string
    nickname?: string
    role?: string
}

const TOKEN_KEY = 'fund_token'
const USER_KEY = 'fund_user'

const state = reactive({
    token: localStorage.getItem(TOKEN_KEY) || '',
    user: JSON.parse(localStorage.getItem(USER_KEY) || 'null') as AuthUser | null,
})

export const isAuthenticated = computed(() => !!state.token)

export function getToken(): string {
    return state.token
}

export function getUser(): AuthUser | null {
    return state.user
}

export function login(token: string, user: AuthUser) {
    state.token = token
    state.user = user
    localStorage.setItem(TOKEN_KEY, token)
    localStorage.setItem(USER_KEY, JSON.stringify(user))
}

export function logout() {
    state.token = ''
    state.user = null
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(USER_KEY)
}

class NotificationManager {
    constructor() {
        this.stompClient = null;
        this.connected = false;
        this.userId = null;
        this.notifications = [];
        this.notificationSound = null;
        
        this.init();
    }
    
    /**
     * 초기화
     */
    init() {
        this.getUserId();
        this.loadNotificationSound();
        this.requestNotificationPermission();
        this.connectWebSocket();
        this.setupNotificationUI();
        this.loadInitialNotifications();
    }
    
    /**
     * 사용자 ID 추출
     */
    getUserId() {
        const userIdElement = document.querySelector('[data-user-id]');
        this.userId = userIdElement ? userIdElement.dataset.userId : null;
        console.log('사용자 ID:', this.userId);
        
        if (!this.userId) {
            console.warn('사용자 ID를 찾을 수 없습니다. 알림 기능이 제한됩니다.');
        }
    }
    
    /**
     * 알림 사운드 로드
     */
    loadNotificationSound() {
        try {
            // 간단한 beep 사운드 생성 (실제로는 sound 파일 사용)
            this.notificationSound = new Audio('data:audio/wav;base64,UklGRnoGAABXQVZFZm10IBAAAAABAAEAQB8AAEAfAAABAAgAZGF0YQoGAACBhYqFbF1fdJivrJBhNjVgodDbq2EcBj+a2/LDciUFLIHO8tiJNwgZaLvt559NEAxQp+PwtmMcBjiR1/LMeSwFJHfH8N2QQAoUXrTp66hVFApGn+DyvmUeAzSJ1e/CdSgGLYPO8tiINwgZaLvt559NEAxPqOPyvmUeA');
        } catch (e) {
            console.warn('알림 사운드 로드 실패:', e);
        }
    }
    
    /**
     * 브라우저 알림 권한 요청
     */
    async requestNotificationPermission() {
        if (!("Notification" in window)) {
            console.warn('이 브라우저는 알림을 지원하지 않습니다.');
            return false;
        }
        
        if (Notification.permission === "granted") {
            console.log('알림 권한이 이미 허용되었습니다.');
            return true;
        }
        
        if (Notification.permission === "denied") {
            console.warn('알림 권한이 거부되었습니다.');
            return false;
        }
        
        try {
            const permission = await Notification.requestPermission();
            if (permission === "granted") {
                console.log('알림 권한이 허용되었습니다.');
                this.showTestNotification();
                return true;
            } else {
                console.warn('알림 권한이 거부되었습니다.');
                return false;
            }
        } catch (error) {
            console.error('알림 권한 요청 실패:', error);
            return false;
        }
    }
    
    /**
     * 테스트 알림 표시
     */
    showTestNotification() {
        const notification = new Notification('알림 허용 완료!', {
            body: '이제 중요한 일정을 놓치지 않으실 수 있습니다.',
            icon: '/favicon.ico',
            tag: 'test-notification'
        });
        
        setTimeout(() => notification.close(), 3000);
    }
    
    /**
     * WebSocket 연결
     */
    connectWebSocket() {
        if (!this.userId) {
            console.warn('사용자 ID가 없어 WebSocket 연결을 할 수 없습니다.');
            return;
        }
        
        try {
            const socket = new SockJS('/ws-notifications');
            this.stompClient = Stomp.over(socket);
            
            // 연결 설정
            this.stompClient.connect({}, 
                (frame) => {
                    console.log('✅ WebSocket 연결 성공:', frame);
                    this.connected = true;
                    this.subscribeToNotifications();
                },
                (error) => {
                    console.error('❌ WebSocket 연결 실패:', error);
                    this.connected = false;
                    
                    // 5초 후 재연결 시도
                    setTimeout(() => this.connectWebSocket(), 5000);
                }
            );
        } catch (error) {
            console.error('WebSocket 초기화 실패:', error);
        }
    }
    
    /**
     * 알림 구독
     */
    subscribeToNotifications() {
        if (!this.stompClient || !this.connected) return;
        
        try {
            // 개인 알림 채널 구독
            this.stompClient.subscribe(`/user/queue/notifications`, (message) => {
                const notification = JSON.parse(message.body);
                console.log('📱 실시간 알림 수신:', notification);
                this.handleNotification(notification);
            });
            
            console.log('🔔 알림 구독 완료');
        } catch (error) {
            console.error('알림 구독 실패:', error);
        }
    }
    
    /**
     * 실시간 알림 처리
     */
    handleNotification(notification) {
        // 알림 목록에 추가
        this.notifications.unshift(notification);
        
        // 최대 50개만 유지
        if (this.notifications.length > 50) {
            this.notifications = this.notifications.slice(0, 50);
        }
        
        // UI 업데이트
        this.updateNotificationBadge();
        
        // 브라우저 알림 표시
        this.showBrowserNotification(notification);
        
        // 사운드 재생
        this.playNotificationSound();
    }
    
    /**
     * 브라우저 알림 표시
     */
    showBrowserNotification(notification) {
        if (Notification.permission !== "granted") return;
        
        try {
            const browserNotification = new Notification(notification.title, {
                body: notification.message,
                icon: '/favicon.ico',
                requireInteraction: true,
                tag: notification.id
            });
            
            // 클릭 이벤트
            browserNotification.onclick = () => {
                window.focus();
                this.handleNotificationClick(notification.type, notification.id, notification.read);
                browserNotification.close();
            };
            
            // 5초 후 자동 닫기
            setTimeout(() => browserNotification.close(), 5000);
            
        } catch (error) {
            console.error('브라우저 알림 표시 실패:', error);
        }
    }
    
    /**
     * 알림 사운드 재생
     */
    playNotificationSound() {
        if (this.notificationSound) {
            try {
                this.notificationSound.currentTime = 0;
                this.notificationSound.play().catch(e => {
                    console.warn('알림 사운드 재생 실패:', e);
                });
            } catch (error) {
                console.warn('알림 사운드 재생 실패:', error);
            }
        }
    }
    
    /**
     * 알림 배지 업데이트
     */
    updateNotificationBadge() {
        const unreadCount = this.notifications.filter(n => !n.read).length;
        const badge = document.querySelector('.notification-badge');
        
        if (badge) {
            if (unreadCount > 0) {
                badge.textContent = unreadCount > 99 ? '99+' : unreadCount;
                badge.style.display = 'flex';
                badge.classList.add('show');
            } else {
                badge.style.display = 'none';
                badge.classList.remove('show');
            }
        }
    }
    
    /**
     * 초기 알림 목록 로드
     */
    async loadInitialNotifications() {
        try {
            const response = await fetch('/api/notifications');
            const data = await response.json();
            
            if (data.success) {
                // API에서 받은 알림들을 배열로 변환
                this.notifications = Object.values(data.notifications || {});
                this.updateNotificationBadge();
                
                console.log('📋 초기 알림 로드 완료:', this.notifications.length + '개');
            }
        } catch (error) {
            console.error('초기 알림 로드 실패:', error);
        }
    }
    
    /**
     * 알림 클릭 처리
     */
    handleNotificationClick(type, notificationId, isRead) {
        // 읽음 처리
        if (!isRead) {
            this.markAsRead(notificationId);
        }
        
        // 타입별 액션
        switch(type) {
            case 'schedule':
                window.open('/schedule/manager', '_blank');
                break;
            case 'chatbot':
                if (window.openChatbot) {
                    window.openChatbot();
                }
                break;
            default:
                console.log('알림 클릭:', type);
        }
    }
    
    /**
     * 특정 알림 읽음 처리
     */
    async markAsRead(notificationId) {
        try {
            const response = await fetch(`/api/notifications/${notificationId}/read`, {
                method: 'PUT'
            });
            const data = await response.json();
            
            if (data.success) {
                // 로컬 알림 상태 업데이트
                const notification = this.notifications.find(n => n.id === notificationId);
                if (notification) {
                    notification.read = true;
                }
                this.updateNotificationBadge();
            }
        } catch (error) {
            console.error('알림 읽음 처리 실패:', error);
        }
    }
    
    /**
     * 모든 알림 읽음 처리
     */
    async markAllAsRead() {
        try {
            const response = await fetch('/api/notifications/read-all', {
                method: 'PUT'
            });
            const data = await response.json();
            
            if (data.success) {
                // 모든 로컬 알림을 읽음으로 표시
                this.notifications.forEach(n => n.read = true);
                this.updateNotificationBadge();
                
                console.log('✅ 모든 알림 읽음 처리 완료');
            }
        } catch (error) {
            console.error('모든 알림 읽음 처리 실패:', error);
        }
    }
    
    /**
     * 알림 UI 설정
     */
    setupNotificationUI() {
        // 권한 요청 배너 설정은 header.html의 JavaScript에서 처리
        console.log('🎨 알림 UI 설정 완료');
    }
    
    /**
     * 연결 해제
     */
    disconnect() {
        if (this.stompClient && this.connected) {
            this.stompClient.disconnect();
            this.connected = false;
            console.log('WebSocket 연결 해제');
        }
    }
}

// 전역 NotificationManager 인스턴스
let notificationManager = null;

// DOM 로드 완료 시 초기화
document.addEventListener('DOMContentLoaded', () => {
    console.log('🔔 NotificationManager 초기화 시작');
    notificationManager = new NotificationManager();
    
    // 전역 함수들을 window에 등록 (header.html에서 사용)
    window.notificationManager = notificationManager;
});

// 페이지 언로드 시 연결 해제
window.addEventListener('beforeunload', () => {
    if (notificationManager) {
        notificationManager.disconnect();
    }
});
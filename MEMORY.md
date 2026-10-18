# Contexto Ativo — Atualizado em 2026-10-18 17:50

## Projetos Ativos
| Projeto | Status | Última Sessão | Próxima Ação |
|---------|--------|---------------|--------------|
| MyMusic | ativo  | Fase 2.7      | Fase 3: Home charts (SoundCloud New & hot / Top 50, кэш в Room) e Library |

## Tarefas Pendentes
### Alta Prioridade
- [ ] Fase 3: Home charts (SoundCloud New & hot / Top 50, кэш в Room) и Library (Лайкнутые треки)
- [x] Fase 2.7: Хотфикс UI и состояний плеера (сброс ошибок, Palette, Crossfade 500мс, maxLines=2)

### Média Prioridade
- [ ] Fase 4: Моя волна (алгоритм рекомендаций на основе истории и лайков)

### Baixa Prioridade
- [ ] Fase 4: Моя волна (алгоритм рекомендаций на основе истории и лайков)
- [ ] v2: Подключение Spotify, Яндекс Музыки, ВК Музыки

## Decisões Recentes
- [Fase 2.5] Использовать троттлинг слайдера и раздельные состояния буферизации
- [Fase 2.6] Глушить воспроизведение сразу в момент нажатия на трек
- [Fase 2.7] Стандартный Slider с кастомной буферизацией и плавный Crossfade 500мс при старте буферизации

## Bloqueadores Ativos
- Nenhum

## Convenções Estabelecidas
- MVVM + Clean Architecture (Data / Domain / Feature / Core)
- Material 3 Design Tokens (M3 Expressive)
- Сборка на слабом ноутбуке без запуска эмулятора и Android Studio
- Запрет на повышение версий AGP выше 8.5.2 и смену библиотек
- Комментарии в коде строго на русском языке

## Últimas Sessões
- Fase 2.5: Хотфикс кнопки Play/Pause, сброс позиции, буферизация
- Fase 2.6: Полировка переключения треков и слайдера
